package org.montezuma.test.traffic.writing.cases;

import org.montezuma.test.traffic.CasesCommon;
import org.montezuma.test.traffic.recording.cases.PassThroughClassTrafficRecorder;
import org.montezuma.test.traffic.writing.TrafficToUnitTestsWriter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import analysethis.utils.math.MonitoredClass;

public class PassThroughClassNoDummyThirdPartyMockingTestWriter {

	public static void main(String[] args) throws FileNotFoundException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
		final List<String> dontMockClasses = new ArrayList<>();
		dontMockClasses.add(".*DummyThirdParty");
		dontMockClasses.addAll(TrafficToUnitTestsWriter.getDontMockClasses());
		CasesCommon.generateTestsFor(MonitoredClass.class, dontMockClasses, PassThroughClassTrafficRecorder.PASSTHROUGH_CLASS_RECORDING_SUBDIR, CasesCommon.parseArguments(args));
	}

}
