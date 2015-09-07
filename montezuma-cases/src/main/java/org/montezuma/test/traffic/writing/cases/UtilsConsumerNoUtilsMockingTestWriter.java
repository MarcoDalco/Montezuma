package org.montezuma.test.traffic.writing.cases;

import analysethis.utils.consumer.UtilsConsumer;

import org.montezuma.test.traffic.CasesCommon;
import org.montezuma.test.traffic.recording.cases.UtilsConsumerTrafficRecorder;
import org.montezuma.test.traffic.writing.TrafficToUnitTestsWriter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class UtilsConsumerNoUtilsMockingTestWriter {

	public static void main(String[] args) throws FileNotFoundException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
		final List<String> dontMockClasses = new ArrayList<>();
		dontMockClasses.add(".*BigDecimalUtils");
		dontMockClasses.addAll(TrafficToUnitTestsWriter.getDontMockClasses());
		new TrafficToUnitTestsWriter().generateTestsFor(UtilsConsumer.class, dontMockClasses, UtilsConsumerTrafficRecorder.UTILS_CONSUMER_RECORDING_SUBDIR, CasesCommon.TEST_CLASS_PATH);
	}

}
