/*
 * Copyright 2012-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.logging.logback.v13;

import ch.qos.logback.classic.joran.sanity.IfNestedWithinSecondPhaseElementSC;
import ch.qos.logback.classic.model.LoggerModel;
import ch.qos.logback.classic.model.RootLoggerModel;
import ch.qos.logback.core.joran.sanity.Pair;
import ch.qos.logback.core.joran.sanity.SanityChecker;
import ch.qos.logback.core.model.AppenderModel;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.spi.ContextAwareBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link SanityChecker} to ensure that {@code springProfile} elements are not nested
 * within second-phase elements.
 *
 * @author Phillip Webb
 * @see IfNestedWithinSecondPhaseElementSC
 */
public class SpringProfileIfNestedWithinSecondPhaseElementSanityChecker extends ContextAwareBase
        implements SanityChecker {

    private static final List<Class<? extends Model>> SECOND_PHASE_TYPES =
            Arrays.asList(AppenderModel.class, LoggerModel.class, RootLoggerModel.class);

    @Override
    public void check(Model model) {
        List<Model> models = new ArrayList<>();
        SECOND_PHASE_TYPES.forEach(type -> deepFindAllModelsOfType(type, models, model));
        List<Pair<Model, Model>> nestedPairs = deepFindNestedSubModelsOfType(SpringProfileModel.class, models);
        if (!nestedPairs.isEmpty()) {
            addWarn("<springProfile> elements cannot be nested within an <appender>, <logger> or <root> element");
            nestedPairs.forEach(nested -> {
                Model first = nested.first;
                Model second = nested.second;
                addWarn(String.format(
                        "Element <%s> at line %s contains a nested <%s> element at line %s",
                        first.getTag(), first.getLineNumber(), second.getTag(), second.getLineNumber()));
            });
        }
    }
}
