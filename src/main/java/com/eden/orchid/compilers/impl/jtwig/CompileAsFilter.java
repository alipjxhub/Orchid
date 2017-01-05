package com.eden.orchid.compilers.impl.jtwig;

import com.eden.orchid.Orchid;
import com.eden.orchid.utilities.AutoRegister;
import org.json.JSONObject;
import org.jtwig.functions.FunctionRequest;
import org.jtwig.functions.JtwigFunction;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@AutoRegister
public class CompileAsFilter implements JtwigFunction {

    static {
        JTwigCompiler.config.functions().add(new CompileAsFilter());
    }

    @Override
    public String name() {
        return "compileAs";
    }

    @Override
    public Collection<String> aliases() {
        return Collections.emptyList();
    }

    @Override
    public Object execute(FunctionRequest request) {

        List<Object> fnParams = request.maximumNumberOfArguments(2)
                                       .minimumNumberOfArguments(2)
                                       .getArguments();

        String text = fnParams.get(0).toString();
        String extension = fnParams.get(1).toString();

        return Orchid.getTheme().compile(extension, text, new JSONObject(Orchid.getRoot().toMap()));
    }
}