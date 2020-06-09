// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import ballerina/io;
import ballerina/java;


// Errors will need to be defined like FUNCTION_SIGNATURE_MISMATCH


# Mock Function object definition

public type MockFunction object {
    string mockFunction = "";
    string caseId = "";
};


# Case object definition

public type FunctionCase object {
    string mockFunction = "";
    MockFunction mockFunctionObj;
    any[] argList = [];
    any returnValue = "";

    // Create a case for a particular MockFunction obj
    public function __init(MockFunction mockFunctionObj) {
        self.mockFunctionObj = mockFunctionObj;
    }

    // Set which mock function is supposed to be called
    public function call(string mockFunction) {
        io:println("[test:FunctionCase] (call) Setting mock function : ", mockFunction);
        self.mockFunction = mockFunction;

        // Call callExt to set the Case in the registry

        io:println("[test:FunctionCase] (call) Calling ext function");
        callExt(self);

    }

    // Return a specific value
    //public function thenReturn(any returnArg) {
    //    self.returnArg = returnArg;
    //    }
    //
    //
    //    // Returnn a specific value when specific args are passed
    //    public function withArguments(any... args) returns Case {
    //        self.args = args;
    //        return self;
    //    }

};


// Creates a new function case object
// Assigns the MockFunction object to the case

public function when(MockFunction mockFunctionObj) returns FunctionCase {
    io:println("[test] (when) Creating new Function Case obj");
    FunctionCase mockCase = new FunctionCase(mockFunctionObj);
    return mockCase;
}


// Call the call function in the Mock.java class
// This will handle adding the case to the registry
public function callExt(object{} case) returns () = @java:Method {
    name : "call",
    class : "org.ballerinalang.testerina.natives.test.FunctionMock"
} external;
