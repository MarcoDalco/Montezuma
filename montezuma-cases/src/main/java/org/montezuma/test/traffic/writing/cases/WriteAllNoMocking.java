package org.montezuma.test.traffic.writing.cases;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class WriteAllNoMocking {

	public static void main(String[] args) throws FileNotFoundException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
		BigDecimalUtilsTestWriter.main(args);
		BoundaryChecksTestWriter.main(args);
		CompiledStatementStoringPreparedStatementCreatorTestWriter.main(args);
		CurrencyUtilsTestWriter.main(args);
		PassThroughClassNoDummyThirdPartyMockingTestWriter.main(args);
		SuperClassCallWithStateTestWriter.main(args);
		TimeConsumerTestWriter.main(args);
		UtilsConsumerNoUtilsMockingTestWriter.main(args);
	}

}
