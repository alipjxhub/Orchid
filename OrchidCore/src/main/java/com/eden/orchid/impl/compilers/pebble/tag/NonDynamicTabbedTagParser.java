package com.eden.orchid.impl.compilers.pebble.tag;

import com.eden.common.util.EdenUtils;
import com.eden.orchid.api.OrchidContext;
import com.eden.orchid.api.compilers.TemplateTag;
import com.eden.orchid.api.theme.pages.OrchidPage;
import com.eden.orchid.impl.compilers.pebble.PebbleWrapperTemplateTag;
import com.mitchellbosecke.pebble.error.ParserException;
import com.mitchellbosecke.pebble.lexer.Token;
import com.mitchellbosecke.pebble.lexer.TokenStream;
import com.mitchellbosecke.pebble.node.RenderableNode;
import com.mitchellbosecke.pebble.node.expression.Expression;
import com.mitchellbosecke.pebble.parser.Parser;
import com.mitchellbosecke.pebble.template.EvaluationContextImpl;
import com.mitchellbosecke.pebble.template.PebbleTemplateImpl;
import com.mitchellbosecke.pebble.utils.StringUtils;

import javax.inject.Provider;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NonDynamicTabbedTagParser extends BaseTagParser {

    private final String[] tagParameters;
    private final Class<? extends TemplateTag> tagClass;
    private final String[] tabParameters;
    private final Class<? extends TemplateTag.Tab> tabClass;

    private Map<String, Expression<?>> paramExpressionMap;
    private List<Expression<?>> bodyFilters;

    private Map<String, Map<String, Expression<?>>> tagBodyParams;
    private Map<String, Expression<?>> tagBodyExpressions;

    public NonDynamicTabbedTagParser(
            Provider<OrchidContext> contextProvider,
            String name,
            String[] tagParameters,
            Class<? extends TemplateTag> tagClass,
            String[] tabParameters,
            Class<? extends TemplateTag.Tab> tabClass
    ) {
        super(contextProvider, name);
        this.tagParameters = tagParameters;
        this.tagClass = tagClass;
        this.tabParameters = tabParameters;
        this.tabClass = tabClass;
    }

    public RenderableNode parse(Token token, Parser parser) throws ParserException {
        TokenStream stream = parser.getStream();
        int lineNumber = token.getLineNumber();

        tagBodyParams = new LinkedHashMap<>();
        tagBodyExpressions = new LinkedHashMap<>();

        // get Tag params
        paramExpressionMap = parseParams(tagParameters, true, tagClass, stream, parser);

        // get Tag filters, which are applied to all tabs
        bodyFilters = parseBodyFilters(stream, parser);
        stream.expect(Token.Type.EXECUTE_END);

        // parse tabs
        while(true) {
            // optionally allow line-breaks between tabs
            if(stream.current().test(Token.Type.TEXT)) {
                // do not allow text to be outside of a tab
                Token textToken = stream.expect(Token.Type.TEXT);
                if (!EdenUtils.isEmpty(textToken.getValue())) {
                    throw new ParserException(null, "Error parsing " + name + " tabbed tag: Text must be included within a tab.", lineNumber, "");
                }
            }

            // start parsing tab
            stream.expect(Token.Type.EXECUTE_START);

            // this is actually the final Tag's closing tag, so exit the loop
            if(stream.current().test(Token.Type.NAME, "end" + name)) break;

            // get tab name
            Token tabNameToken = stream.expect(Token.Type.NAME);
            String tabName = tabNameToken.getValue();

            // make sure we only have one of each tab key
            if(tagBodyExpressions.containsKey(tabName)) {
                throw new ParserException(null, "Error parsing " + name + " tabbed tag: Tab " + tabName + " already used.", lineNumber, "");
            }

            // get tab params
            Map<String, Expression<?>> tabParams = parseParams(tabParameters, true, tabClass, stream, parser);
            tagBodyParams.put(tabName, tabParams);

            // get tab filters
            List<Expression<?>> tabBodyFilters = parseBodyFilters(stream, parser);
            List<Expression<?>> fullTabFilters = Stream.concat(tabBodyFilters.stream(), bodyFilters.stream()).collect(Collectors.toList());

            // end opening tab
            stream.expect(Token.Type.EXECUTE_END);

            // subcompile until the end of the tab, which closes this tab
            Expression<?> tabBody = parseBody(fullTabFilters, tabName, stream, parser);
            tagBodyExpressions.put(tabName, tabBody);
        }
        stream.expect(Token.Type.NAME, "end" + name);
        stream.expect(Token.Type.EXECUTE_END);

        return new PebbleWrapperTemplateTag.TemplateTagNode(lineNumber, this);
    }

    @Override
    public void render(PebbleTemplateImpl self, Writer writer, EvaluationContextImpl context) throws IOException {
        OrchidContext orchidContext = contextProvider.get();

        // create a new tag
        TemplateTag freshTag = orchidContext.resolve(tagClass);

        // evaluate its own params and populate the main Tag class with them
        Map<String, Object> evaluatedParamExpressionMap = evaluateParams(paramExpressionMap, self, context);

        Object pageVar = context.getVariable("page");
        final OrchidPage actualPage;
        if (pageVar instanceof OrchidPage) {
            actualPage = (OrchidPage) pageVar;
        }
        else {
            actualPage = null;
        }

        freshTag.extractOptions(orchidContext, evaluatedParamExpressionMap);

        // populate the content of each tab
        for(Map.Entry<String, Expression<?>> tagBodyExpression : tagBodyExpressions.entrySet()) {

            // get the tab body content
            String key = tagBodyExpression.getKey();
            String bodyContent = StringUtils.toString(tagBodyExpression.getValue().evaluate(self, context)).trim();

            // Get a new Tab instance, evaluate that tab's options, and then populate the Tab instance with them
            TemplateTag.Tab tab = freshTag.getNewTab(key, bodyContent);
            Map<String, Object> tabParams = evaluateParams(tagBodyParams.get(key), self, context);
            tab.extractOptions(orchidContext, tabParams);

            // add the tab to the Tag
            freshTag.setContent(key, tab);
        }

        freshTag.onRender(orchidContext, actualPage);
        if (freshTag.rendersContent()) {
            writer.append(freshTag.renderContent(orchidContext, actualPage));
        }
    }

}
