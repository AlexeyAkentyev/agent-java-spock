/*
 * Copyright (C) 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.spock;

import org.spockframework.runtime.model.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Collections.synchronizedMap;
import static org.spockframework.runtime.model.BlockKind.WHERE;


/**
 * Utility class, which provides static convenience methods for the operations on
 * {@link org.spockframework.runtime.model.NodeInfo} and its derivatives
 *
 * @author Dzmitry Mikhievich
 */
final class NodeInfoUtils {

    @VisibleForTesting
    static final String INHERITED_FIXTURE_NAME_TEMPLATE = "%s:%s";
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final String BLOCK_SPLITTER = ": ";
    private static final String CONJUNCTION_KEYWORD = "And";

    private static final Map<BlockKind, String> BLOCK_NAMES = synchronizedMap(Maps.newEnumMap(BlockKind.class));

    private static final Predicate<BlockInfo> SKIP_BLOCK_CONDITION = info -> {
        if (info != null) {
            boolean isWhereBlock = WHERE.equals(info.getKind());
            if (isWhereBlock) {
                return Iterables.all(info.getTexts(), Strings::isNullOrEmpty);
            }
        }
        return false;
    };

    private NodeInfoUtils() {
    }

    /**
     * Create textual description for the provided feature info.
     * Description includes formatted blocks excluding ones which carry no semantic load.
     * <p><i>Please, be advised that generated description can be differ from the original blocks representation in the code
     * due specifics of the Spock model.</i>
     *
     * @param featureInfo target feature info
     * @return description
     */
    static String buildFeatureDescription(FeatureInfo featureInfo) {
        StringBuilder description = new StringBuilder();
        Iterator<BlockInfo> blocksIterator = featureInfo.getBlocks().iterator();
        while (blocksIterator.hasNext()) {
            BlockInfo block = blocksIterator.next();
            boolean isLast = !blocksIterator.hasNext();
            if (!SKIP_BLOCK_CONDITION.apply(block)) {
                appendBlockInfo(description, block);
                if (!isLast) {
                    description.append(LINE_SEPARATOR);
                }
            } else if (isLast && description.length() > 0) {
                int lastLineSeparatorIndex = description.lastIndexOf(LINE_SEPARATOR);
                description.delete(lastLineSeparatorIndex, description.length());
            }
        }
        return description.toString();
    }

    static String buildIterationDescription(IterationInfo iterationInfo) {
        String featureDescription = buildFeatureDescription(iterationInfo.getFeature());
        return unrollIterationDescription(iterationInfo, featureDescription);
    }

    /**
     * Get display name of the fixture. If fixture is inherited, display name is started from the source specification name.
     *
     * @param methodInfo method info of fixture
     * @param inherited  indicates if fixture is inherited
     * @return display name
     */
    static String getFixtureDisplayName(MethodInfo methodInfo, boolean inherited) {
        if (methodInfo != null) {
            String fixtureName = methodInfo.getName();
            if (inherited) {
                String sourceSpecName = methodInfo.getParent().getReflection().getSimpleName();
                return format(INHERITED_FIXTURE_NAME_TEMPLATE, sourceSpecName, fixtureName);
            }
            return fixtureName;
        }
        return "";
    }

    /**
     * Get unique specification identifier.
     *
     * @param specInfo target spec
     * @return identifier. If null {@code specInfo} is passed, empty identifier will be returned.
     */
    static String getSpecIdentifier(SpecInfo specInfo) {
        return specInfo != null && specInfo.getReflection() != null ? specInfo.getReflection().getName() : "";
    }


    private static String unrollIterationDescription(IterationInfo iterationInfo, String iterationDescription) {
        List<String> parameterNames = iterationInfo.getFeature().getParameterNames();
        Object[] dataValues = iterationInfo.getDataValues();
        if (!parameterNames.isEmpty() && dataValues != null) {
            System.out.println("----=============== SIZE: " + dataValues.length);
            for (String parameterName : parameterNames) {
                Object value = dataValues[0];
                if (value instanceof String) {
                    iterationDescription = iterationDescription
                            .replaceAll("\\{?#" + parameterName + "}?", (String) value);
                }
            }
        }

        return iterationDescription;
    }

    private static void appendBlockInfo(StringBuilder featureDescription, BlockInfo block) {
        featureDescription.append(formatBlockKind(block.getKind())).append(BLOCK_SPLITTER);
        Iterator<String> textsIterator = block.getTexts().iterator();
        // append heading block
        if (textsIterator.hasNext()) {
            featureDescription.append(textsIterator.next());
        }
        // append conjunction blocks
        while (textsIterator.hasNext()) {
            featureDescription.append(LINE_SEPARATOR).append(CONJUNCTION_KEYWORD).append(BLOCK_SPLITTER).append(textsIterator.next());
        }
    }

    /**
     * Get formatted block kind representation.
     *
     * @param blockKind kind
     * @return capitalized block kind name
     */
    private static String formatBlockKind(BlockKind blockKind) {
        if (BLOCK_NAMES.containsKey(blockKind)) {
            return BLOCK_NAMES.get(blockKind);
        } else {
            char[] initialChars = blockKind.name().toCharArray();
            char[] buffer = new char[initialChars.length];
            buffer[0] = initialChars[0];
            // iterate over characters excluding the first one
            for (int i = 1; i < initialChars.length; i++) {
                char ch = initialChars[i];
                buffer[i] = Character.toLowerCase(ch);
            }
            String blockName = new String(buffer);
            BLOCK_NAMES.put(blockKind, blockName);
            return blockName;
        }
    }
}
