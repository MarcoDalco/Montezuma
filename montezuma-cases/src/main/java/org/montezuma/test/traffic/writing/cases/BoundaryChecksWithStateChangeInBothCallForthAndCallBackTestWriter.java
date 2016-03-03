package org.montezuma.test.traffic.writing.cases;

import org.montezuma.test.traffic.CasesCommon;
import org.montezuma.test.traffic.recording.cases.BoundaryChecksWithStateChangeinBothCallForthAndCallBackTrafficRecorder;
import org.montezuma.test.traffic.writing.TrafficToUnitTestsWriter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import analysethis.untestable.until.canruncodebetweenexpectedinvocationandreturningvalue.B;

public class BoundaryChecksWithStateChangeInBothCallForthAndCallBackTestWriter {

	public static void main(String[] args) throws FileNotFoundException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
		CasesCommon.generateTestsFor(
				B.class, TrafficToUnitTestsWriter.getDontMockClasses(), BoundaryChecksWithStateChangeinBothCallForthAndCallBackTrafficRecorder.BOUNDARY_CHECKS_RECORDING_SUBDIR, CasesCommon.parseArguments(args));
	}

}
