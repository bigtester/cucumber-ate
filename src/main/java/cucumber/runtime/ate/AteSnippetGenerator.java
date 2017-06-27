/*******************************************************************************
 * ATE, Automation Test Engine
 *
 * Copyright 2017, Montreal PROT, or individual contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Montreal PROT.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package cucumber.runtime.ate;


import cucumber.api.DataTable;
import cucumber.runtime.snippets.ArgumentPattern;
import cucumber.runtime.snippets.FunctionNameGenerator;
import gherkin.I18n;
import gherkin.formatter.model.Step;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AteSnippetGenerator {
    private static final ArgumentPattern[] DEFAULT_ARGUMENT_PATTERNS = new ArgumentPattern[]{
            new ArgumentPattern(Pattern.compile("\"([^\"]*)\""), String.class),
            new ArgumentPattern(Pattern.compile("(\\d+)"), Integer.TYPE)
    };
    private static final Pattern GROUP_PATTERN = Pattern.compile("\\(");
    private static final Pattern[] ESCAPE_PATTERNS = new Pattern[]{
            Pattern.compile("\\$"),
            Pattern.compile("\\("),
            Pattern.compile("\\)"),
            Pattern.compile("\\["),
            Pattern.compile("\\]"),
            Pattern.compile("\\?"),
            Pattern.compile("\\*"),
            Pattern.compile("\\+"),
            Pattern.compile("\\."),
            Pattern.compile("\\^")
    };

    private static final String REGEXP_HINT = "Write code here that turns the phrase above into concrete actions";

    private final AteSnippet snippet;

    public AteSnippetGenerator(AteSnippet snippet) {
        this.snippet = snippet;
    }

    public String getSnippet(Step step, FunctionNameGenerator functionNameGenerator) {
        return MessageFormat.format(
                snippet.template(),
                I18n.codeKeywordFor(step.getKeyword()),
                snippet.escapePattern(patternFor(step.getName())),
                functionName(step.getName(), functionNameGenerator),
                snippet.arguments(argumentTypes(step)),
                REGEXP_HINT,
                step.getRows() == null ? "" : snippet.tableHint()
        );
    }

    String patternFor(String stepName) {
        String pattern = stepName;
        for (Pattern escapePattern : ESCAPE_PATTERNS) {
            Matcher m = escapePattern.matcher(pattern);
            String replacement = Matcher.quoteReplacement(escapePattern.toString());
            pattern = m.replaceAll(replacement);
        }
        for (ArgumentPattern argumentPattern : argumentPatterns()) {
            pattern = argumentPattern.replaceMatchesWithGroups(pattern);
        }
        if (snippet.namedGroupStart() != null) {
            pattern = withNamedGroups(pattern);
        }

        return "^" + pattern + "$";
    }

    private String functionName(String sentence, FunctionNameGenerator functionNameGenerator) {
        if(functionNameGenerator == null) {
            return null;
        }
        for (ArgumentPattern argumentPattern : argumentPatterns()) {
            sentence = argumentPattern.replaceMatchesWithSpace(sentence);
        }
        return functionNameGenerator.generateFunctionName(sentence);
    }


    private String withNamedGroups(String snippetPattern) {
        Matcher m = GROUP_PATTERN.matcher(snippetPattern);

        StringBuffer sb = new StringBuffer();
        int n = 1;
        while (m.find()) {
            m.appendReplacement(sb, "(" + snippet.namedGroupStart() + n++ + snippet.namedGroupEnd());
        }
        m.appendTail(sb);

        return sb.toString();
    }


    private List<Class<?>> argumentTypes(Step step) {
        String name = step.getName();
        List<Class<?>> argTypes = new ArrayList<Class<?>>();
        Matcher[] matchers = new Matcher[argumentPatterns().length];
        for (int i = 0; i < argumentPatterns().length; i++) {
            matchers[i] = argumentPatterns()[i].pattern().matcher(name);
        }
        int pos = 0;
        while (true) {
            int matchedLength = 1;

            for (int i = 0; i < matchers.length; i++) {
                Matcher m = matchers[i].region(pos, name.length());
                if (m.lookingAt()) {
                    Class<?> typeForSignature = argumentPatterns()[i].type();
                    argTypes.add(typeForSignature);

                    matchedLength = m.group().length();
                    break;
                }
            }

            pos += matchedLength;

            if (pos == name.length()) {
                break;
            }
        }
        if (step.getDocString() != null) {
            argTypes.add(String.class);
        }
        if (step.getRows() != null) {
            argTypes.add(DataTable.class);
        }
        return argTypes;
    }

    ArgumentPattern[] argumentPatterns() {
        return DEFAULT_ARGUMENT_PATTERNS;
    }

    public static String untypedArguments(List<Class<?>> argumentTypes) {
        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < argumentTypes.size(); n++) {
            if (n > 0) {
                sb.append(", ");
            }
            sb.append("arg").append(n + 1);
        }
        return sb.toString();
    }
}
