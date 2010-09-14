/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Copyright (c) 2002-2007, Marc Prud'hommeaux. All rights reserved.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 */
package org.apache.karaf.shell.console.completer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.gogo.commands.Option;
import org.apache.felix.gogo.commands.basic.AbstractCommand;
import org.apache.felix.gogo.commands.basic.DefaultActionPreparator;
import org.apache.karaf.shell.console.CompletableFunction;
import org.apache.karaf.shell.console.Completer;

public class ArgumentCompleter implements Completer {
    final Completer commandCompleter;
    final Completer optionsCompleter;
    final List<Completer> argsCompleters;
    final AbstractCommand function;
    final Map<Option, Field> fields = new HashMap<Option, Field>();
    final Map<String, Option> options = new HashMap<String, Option>();
    boolean strict = true;

    public ArgumentCompleter(AbstractCommand function, String command) {
        this.function = function;
        // Command name completer
        commandCompleter = new StringsCompleter(getNames(command));
        // Build options completer
        for (Class type = function.getActionClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Option option = field.getAnnotation(Option.class);
                if (option != null) {
                    fields.put(option, field);
                    options.put(option.name(), option);
                    String[] aliases = option.aliases();
                    if (aliases != null) {
                        for (String alias : aliases) {
                            options.put(alias, option);
                        }
                    }
                }
            }
        }
        options.put(DefaultActionPreparator.HELP.name(), DefaultActionPreparator.HELP);
        optionsCompleter = new StringsCompleter(options.keySet());
        // Build arguments completers
        argsCompleters = new ArrayList<Completer>();
        if (function instanceof CompletableFunction) {
            List<Completer> fcl = ((CompletableFunction) function).getCompleters();
            if (fcl != null) {
                for (Completer c : fcl) {
                    argsCompleters.add(c == null ? NullCompleter.INSTANCE : c);
                }
            } else {
                argsCompleters.add(NullCompleter.INSTANCE);
            }
        } else {
            argsCompleters.add(NullCompleter.INSTANCE);
        }
    }

    private String[] getNames(String command) {
        String[] s = command.split(":");
        return new String[] { command, s[1] };
    }

    /**
     *  If true, a completion at argument index N will only succeed
     *  if all the completions from 0-(N-1) also succeed.
     */
    public void setStrict(final boolean strict) {
        this.strict = strict;
    }

    /**
     *  Returns whether a completion at argument index N will succees
     *  if all the completions from arguments 0-(N-1) also succeed.
     */
    public boolean getStrict() {
        return this.strict;
    }

    public int complete(final String buffer, final int cursor,
                        final List<String> candidates) {
        ArgumentList list = delimit(buffer, cursor);
        int argpos = list.getArgumentPosition();
        int argIndex = list.getCursorArgumentIndex();

        Completer comp = null;
        String[] args = list.getArguments();
        int index = 0;
        // First argument is command name
        if (index < argIndex) {
            // Verify command name
            if (!verifyCompleter(commandCompleter, args[index])) {
                return -1;
            }
            index++;
        } else {
            comp = commandCompleter;
        }
        // Now, check options
        if (comp == null) {
            while (index < argIndex && args[index].startsWith("-")) {
                if (!verifyCompleter(optionsCompleter, args[index])) {
                    return -1;
                }
                Option option = options.get(args[index]);
                if (option == null) {
                    return -1;
                }
                Field field = fields.get(option);
                if (field != null && field.getType() != boolean.class && field.getType() != Boolean.class) {
                    if (++index == argIndex) {
                        comp = NullCompleter.INSTANCE;
                    }
                }
                index++;
            }
            if (comp == null && index >= argIndex && index < args.length && args[index].startsWith("-")) {
                comp = optionsCompleter;
            }
        }
        // Check arguments
        if (comp == null) {
            int indexArg = 0;
            while (index < argIndex) {
                Completer sub = argsCompleters.get(indexArg >= argsCompleters.size() ? argsCompleters.size() - 1 : indexArg);
                if (!verifyCompleter(sub, args[index])) {
                    return -1;
                }
                index++;
                indexArg++;
            }
            comp = argsCompleters.get(indexArg >= argsCompleters.size() ? argsCompleters.size() - 1 : indexArg);
        }

        int ret = comp.complete(list.getCursorArgument(), argpos, candidates);

        if (ret == -1) {
            return -1;
        }

        int pos = ret + (list.getBufferPosition() - argpos);

        /**
         *  Special case: when completing in the middle of a line, and the
         *  area under the cursor is a delimiter, then trim any delimiters
         *  from the candidates, since we do not need to have an extra
         *  delimiter.
         *
         *  E.g., if we have a completion for "foo", and we
         *  enter "f bar" into the buffer, and move to after the "f"
         *  and hit TAB, we want "foo bar" instead of "foo  bar".
         */
        if ((cursor != buffer.length()) && isDelimiter(buffer, cursor)) {
            for (int i = 0; i < candidates.size(); i++) {
                String val = candidates.get(i);

                while ((val.length() > 0)
                    && isDelimiter(val, val.length() - 1)) {
                    val = val.substring(0, val.length() - 1);
                }

                candidates.set(i, val);
            }
        }

        return pos;
    }

    protected boolean verifyCompleter(Completer completer, String argument) {
        List<String> candidates = new ArrayList<String>();
        return completer.complete(argument, argument.length(), candidates) != -1 && !candidates.isEmpty();
    }

    public ArgumentList delimit(final String buffer, final int cursor) {
        Parser parser = new Parser(buffer, cursor);
        try {
            List<List<List<String>>> program = parser.program();
            List<String> pipe = program.get(parser.c0).get(parser.c1);
            return new ArgumentList(pipe.toArray(new String[pipe.size()]), parser.c2, parser.c3, cursor);
        } catch (Throwable t) {
            return new ArgumentList(new String[] { buffer }, 0, cursor, cursor);
        }
    }

    /**
     *  Returns true if the specified character is a whitespace
     *  parameter. Check to ensure that the character is not
     *  escaped and returns true from
     *  {@link #isDelimiterChar}.
     *
     *  @param  buffer the complete command buffer
     *  @param  pos    the index of the character in the buffer
     *  @return        true if the character should be a delimiter
     */
    public boolean isDelimiter(final String buffer, final int pos) {
        return !isEscaped(buffer, pos) && isDelimiterChar(buffer, pos);
    }

    public boolean isEscaped(final String buffer, final int pos) {
        return pos > 0 && buffer.charAt(pos) == '\\' && !isEscaped(buffer, pos - 1);
    }

    /**
     *  The character is a delimiter if it is whitespace, and the
     *  preceeding character is not an escape character.
     */
    public boolean isDelimiterChar(String buffer, int pos) {
        return Character.isWhitespace(buffer.charAt(pos));
    }

    /**
     *  The result of a delimited buffer.
     *
     *  @author  <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
     */
    public static class ArgumentList {
        private String[] arguments;
        private int cursorArgumentIndex;
        private int argumentPosition;
        private int bufferPosition;

        /**
         *  @param  arguments           the array of tokens
         *  @param  cursorArgumentIndex the token index of the cursor
         *  @param  argumentPosition    the position of the cursor in the
         *                              current token
         *  @param  bufferPosition      the position of the cursor in
         *                              the whole buffer
         */
        public ArgumentList(String[] arguments, int cursorArgumentIndex,
            int argumentPosition, int bufferPosition) {
            this.arguments = arguments;
            this.cursorArgumentIndex = cursorArgumentIndex;
            this.argumentPosition = argumentPosition;
            this.bufferPosition = bufferPosition;
        }

        public void setCursorArgumentIndex(int cursorArgumentIndex) {
            this.cursorArgumentIndex = cursorArgumentIndex;
        }

        public int getCursorArgumentIndex() {
            return this.cursorArgumentIndex;
        }

        public String getCursorArgument() {
            if ((cursorArgumentIndex < 0)
                || (cursorArgumentIndex >= arguments.length)) {
                return null;
            }

            return arguments[cursorArgumentIndex];
        }

        public void setArgumentPosition(int argumentPosition) {
            this.argumentPosition = argumentPosition;
        }

        public int getArgumentPosition() {
            return this.argumentPosition;
        }

        public void setArguments(String[] arguments) {
            this.arguments = arguments;
        }

        public String[] getArguments() {
            return this.arguments;
        }

        public void setBufferPosition(int bufferPosition) {
            this.bufferPosition = bufferPosition;
        }

        public int getBufferPosition() {
            return this.bufferPosition;
        }
    }
}