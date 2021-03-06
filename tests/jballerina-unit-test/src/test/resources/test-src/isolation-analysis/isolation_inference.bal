// Copyright (c) 2021 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import ballerina/jballerina.java;

function hello() returns string => "hello";

function count() returns int => hello().length();

function countExpression() returns int => 1 + hello().length();

string helloGlobString = "hello!";
int globInt = 1;

function helloMutableGlobalVar() returns string => helloGlobString;

function countMutableGlobalVar() returns int {
    return helloGlobString.length();
}

function countHelloMutableGlobalVar() returns int => helloMutableGlobalVar().length();

function countExprMutableGlobalVar() returns int => globInt + helloMutableGlobalVar().length();

public function helloPublic() returns string => "hello";

public function countPublic() returns int => hello().length();

public function countExpressionPublic() returns int => 1 + hello().length();

public function helloMutableGlobalVarPublic() returns string => helloGlobString;

public function countMutableGlobalVarPublic() returns int {
    return helloGlobString.length();
}

public function countHelloMutableGlobalVarPublic() returns int => helloMutableGlobalVar().length();

public function countExprMutableGlobalVarPublic() returns int => globInt + helloMutableGlobalVar().length();

function testBasicFunctionIsolationInference() {
    assertTrue(<any> hello is isolated function);
    assertTrue(count is isolated function () returns int);
    assertTrue(countExpression is isolated function () returns int);
    assertFalse(<any> helloMutableGlobalVar is isolated function);
    assertFalse(countMutableGlobalVar is isolated function () returns int);
    assertFalse(countHelloMutableGlobalVar is isolated function () returns int);
    assertFalse(countExprMutableGlobalVar is isolated function () returns int);
    assertFalse(<any> helloPublic is isolated function);
    assertFalse(countPublic is isolated function () returns int);
    assertFalse(countExpressionPublic is isolated function () returns int);
    assertFalse(<any> helloMutableGlobalVarPublic is isolated function);
    assertFalse(countMutableGlobalVarPublic is isolated function () returns int);
    assertFalse(countHelloMutableGlobalVarPublic is isolated function () returns int);
    assertFalse(countExprMutableGlobalVarPublic is isolated function () returns int);
}

function func1(string str) returns string {
    return func2(str);
}

function func2(string str) returns string {
    if str.length() < 4 {
        return func1(str + "!");
    }
    return str;
}

function func3(string str) returns string {
    return func4(helloGlobString + str);
}

function func4(string str) returns string {
    if str.length() < 4 {
        return func3(str + "!");
    }
    return str;
}

function func5() returns string {
    boolean b = true;

    if b {
        return func6("hello");
    }
    return func1("hello");
}

function func6(string str) returns string {
    return func5() + helloGlobString;
}

function testRecursiveFunctionIsolationInference() {
    assertTrue(<any> func1 is isolated function);
    assertTrue(func2 is isolated function (string str) returns string);
    assertTrue(<any> func3 is function (string str) returns string);
    assertFalse(func3 is isolated function (string str) returns string);
    assertTrue(<any> func4 is function (string str) returns string);
    assertFalse(func4 is isolated function (string str) returns string);
    assertTrue(<any> func5 is function () returns string);
    assertFalse(<any> func5 is isolated function () returns string);
    assertTrue(<any> func6 is function (string str) returns string);
    assertFalse(func6 is isolated function (string str) returns string);
}

client class NonPublicNonIsolatedClass {
    string str = "hello";

    remote function foo() returns int => self.bar().length();

    function bar() returns string => self.str;
}

isolated client class NonPublicIsolatedClass {
    private string str = "hello";

    remote function foo() returns int {
        lock {
            return 1;
        }
    }

    function bar() returns string {
        lock {
            return self.str;
        }
    }
}

public isolated client class PublicIsolatedClass {
    private string str = "hello";

    remote function foo() returns int {
        lock {
            return 1;
        }
    }

    function bar() returns string {
        lock {
            return self.str;
        }
    }
}

function testMethodIsolationInference() {
    NonPublicNonIsolatedClass c1 = new;
    NonPublicIsolatedClass c2 = new;
    PublicIsolatedClass c3 = new;

    // https://github.com/ballerina-platform/ballerina-lang/issues/27917
    // assertFalse(<any> c1.foo is isolated function);
    // assertFalse(<any> c1.bar is isolated function);

    // assertTrue(<any> c2.foo is isolated function);
    // assertTrue(<any> c2.bar is isolated function);

    // assertFalse(<any> c3.foo is isolated function);
    // assertFalse(<any> c3.bar is isolated function);

    assertTrue(isMethodIsolated(c1, "foo"));
    assertTrue(isMethodIsolated(c1, "bar"));

    assertTrue(isMethodIsolated(c2, "foo"));
    assertTrue(isMethodIsolated(c2, "bar"));

    assertFalse(isMethodIsolated(c3, "foo"));
    assertFalse(isMethodIsolated(c3, "bar"));
}

function functionWithFunctioPointerCall1() {
    string[] strArr = [];

    var closure = function (string val) {
        strArr[0] = val;
    };
    closure("foo");
}

function functionWithFunctioPointerCall2() {
    var closure = isolated function (string val) {
        boolean b = val.length() == 0;
    };
    closure("foo");
}

function functionWithFunctioPointerCall3() {
    string[] strArr = [];

    var closure = function (string val) { // Not called.
        strArr[0] = val;
    };
}

function testFunctionPointerIsolationInference() {
    assertFalse(<any> functionWithFunctioPointerCall1 is isolated function);
    assertTrue(<any> functionWithFunctioPointerCall2 is isolated function);
    assertTrue(<any> functionWithFunctioPointerCall3 is isolated function);
}

service class NonPublicServiceClass {
    resource function get foo() {
        self.func(hello());
    }

    remote function bar() {
        NonPublicIsolatedClass cl = new;
        self.func(cl.bar());
    }

    function func(string str) {
        int length = str.length();
    }

    public function func2(string str) {
        int length = str.length();
    }

    function func3(string str) {
        helloGlobString = str;
    }
}

public service class PublicServiceClass {
    resource function get foo() {
        self.func(hello());
    }

    remote function bar() {
        NonPublicIsolatedClass cl = new;
        self.func(cl.bar());
    }

    function func(string str) {
        int length = str.length();
    }

    public function func2(string str) {
        int length = str.length();
    }

    function func3(string str) {
        helloGlobString = str;
    }
}

function testServiceClassMethodIsolationInference() {
    assertTrue(isResourceIsolated(NonPublicServiceClass, "get", "foo"));
    assertTrue(isRemoteMethodIsolated(NonPublicServiceClass, "bar"));
    assertTrue(isMethodIsolated(NonPublicServiceClass, "func"));
    assertFalse(isMethodIsolated(NonPublicServiceClass, "func2"));
    assertFalse(isMethodIsolated(NonPublicServiceClass, "func3"));

    assertFalse(isResourceIsolated(PublicServiceClass, "get", "foo"));
    assertFalse(isRemoteMethodIsolated(PublicServiceClass, "bar"));
    assertFalse(isMethodIsolated(PublicServiceClass, "func"));
    assertFalse(isMethodIsolated(PublicServiceClass, "func2"));
    assertFalse(isMethodIsolated(PublicServiceClass, "func3"));
}

service on new Listener() {
    private string str = "abc";

    resource function get foo() returns string => hello();

    resource function get bar() returns string => helloMutableGlobalVar();

    remote function baz() returns string => self.quux();

    remote function qux() returns string => self.quuz();

    function quux() returns string => self.str;

    function quuz() returns string => helloMutableGlobalVar();
}

class Listener {

    public function attach(service object {} s, string|string[]? name = ()) returns error?  = @java:Method {
                                       name: "testServiceDeclarationMethodIsolationInference",
                                       'class: "org.ballerinalang.test.isolation.IsolationInferenceTest"
                                   } external;

    public function detach(service object {} s) returns error? { }

    public function 'start() returns error? { }

    public function gracefulStop() returns error? { }

    public function immediateStop() returns error? { }
}

isolated function isResourceIsolated(service object {}|typedesc val, string resourceMethodName,
     string resourcePath) returns boolean = @java:Method {
                        'class: "org.ballerinalang.test.isolation.IsolationInferenceTest",
                        paramTypes: ["java.lang.Object", "io.ballerina.runtime.api.values.BString",
                                        "io.ballerina.runtime.api.values.BString"]
                    } external;

isolated function isRemoteMethodIsolated(object {}|typedesc val, string methodName) returns boolean = @java:Method {
                                            'class: "org.ballerinalang.test.isolation.IsolationInferenceTest",
                                             paramTypes: ["java.lang.Object", "io.ballerina.runtime.api.values.BString"]
                                        } external;

isolated function isMethodIsolated(object {}|typedesc val, string methodName) returns boolean = @java:Method {
                                            'class: "org.ballerinalang.test.isolation.IsolationInferenceTest",
                                            paramTypes: ["java.lang.Object", "io.ballerina.runtime.api.values.BString"]
                                        } external;

isolated function assertTrue(anydata actual) => assertEquality(true, actual);

isolated function assertFalse(anydata actual) => assertEquality(false, actual);

isolated function assertEquality(anydata expected, anydata actual) {
    if expected == actual {
        return;
    }

    panic error(string `expected '${expected.toString()}', found '${actual.toString()}'`);
}
