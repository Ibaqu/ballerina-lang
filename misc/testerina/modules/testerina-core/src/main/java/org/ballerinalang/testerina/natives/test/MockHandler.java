package org.ballerinalang.testerina.natives.test;

import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.jvm.values.StringValue;

public class MockHandler {

    public static Object mockHandler(ObjectValue mockFunction) {
        // extract Case Id info
        String caseId = mockFunction.getStringValue("caseId");
        // Get instancce
        return MockRegistry.getInstance().getCase(caseId); //returns specified value
    }

}



//if( ) { // is string and prefix
//            // how bal functions are called thru java
//            // get testable jar, decompiled java classes
//            // manu
//        } else {
//            // return
//        }
//        // prefix for call :  __call__mockIntAdd