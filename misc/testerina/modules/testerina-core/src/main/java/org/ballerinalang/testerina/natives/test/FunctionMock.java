package org.ballerinalang.testerina.natives.test;

import org.ballerinalang.jvm.values.ArrayValue;
import org.ballerinalang.jvm.values.ErrorValue;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.jvm.values.connector.Executor;

public class FunctionMock {

    public static void call(ObjectValue caseObj) {
        System.out.println("[FunctionMock] (call) Call function Hit");

        ObjectValue mockFunctionObj = caseObj.getObjectValue("mockFunctionObj");
        String mockFunction = caseObj.getStringValue("mockFunction");
        ArrayValue args = caseObj.getArrayValue("argList");
        Object returnVal = caseObj.get("returnValue");

        MockRegistry.getInstance().addNewCase(mockFunctionObj, mockFunction, args, returnVal);



        // Add the case to the registry
        // MockRegistry.getInstance().addNewCase(mockFunction, functionName, args, returnValue)
    }

}
