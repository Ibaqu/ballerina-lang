// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
import ballerina/test;
import utils;

const int length = 3;

// array-type-descriptor := member-type-descriptor [ [ array-length ] ]
// member-type-descriptor := type-descriptor
// array-length := int-literal | constant-reference-expr | implied-array-length
// implied-array-length := ! ...
// TODO: Support constant-reference-expr as array-length
//@test:Config {
//    groups: ["broken"]
//}
//function testArrayTypeDescriptorBroken() {
//    int[] array1 = [];
//    int[] expectedArray = [];
//    test:assertEquals(array1, expectedArray, msg = "expected fixed length array and implied length array to be equal");
//
//    string[length] array2 = ["a", "b", "c"];
//    string[!...] array3 = ["a", "b", "c"];
//    test:assertEquals(array2, array3, msg = "expected fixed length array and implied length array to be equal");
//}

// The member type of an array type can be any type T (including arrays), provided only that T
// has an implicit initial value. (When T does not have an implicit initial value, an array of T?
// may be used instead; see Optional Types.)
// TODO: Array member type can not be a type which has no implicit initial value
@test:Config {
    groups: ["broken"]
}
function testArrayMemberTypesBroken() {
    utils:FooObject[] objectArray = []; // This should fail at compile time
}
