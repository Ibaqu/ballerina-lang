/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.langlib.value;

import org.ballerinalang.jvm.TypeChecker;
import org.ballerinalang.jvm.types.BType;
import org.ballerinalang.jvm.types.TypeTags;
import org.ballerinalang.jvm.values.ErrorValue;
import org.ballerinalang.jvm.values.TypedescValue;

/**
 * Extern function lang.values:requireType.
 *
 * @since 2.0.0
 */
public class RequireType {
    public static Object requireType(Object value, TypedescValue type) {
        if (TypeChecker.getType(value).getTag() == TypeTags.ERROR_TAG) {
            return value;
        }
        return convert(type.getDescribingType(), value);
    }

    public static Object convert(BType convertType, Object inputValue) {
        try {
             return TypeChecker.checkCast(inputValue, convertType);
        } catch (ErrorValue e) {
            return e;
        }
    }
}
