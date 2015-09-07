package org.montezuma.test.traffic.recording.cases;

import analysethis.superclasscall.withstate.SomeClass;

import org.montezuma.test.traffic.recording.TrafficRecorder;
import org.montezuma.test.traffic.recording.aop.aspects.RecordingAspect;

import java.sql.SQLException;

public class SuperClassCallWithStateTrafficRecorder {
	public static final String	SUPERCLASS_CALL_RECORDING_SUBDIR	= "superclasscall";

	public static void main(String[] args) throws ClassNotFoundException, SQLException {
		new TrafficRecorder().runRecording(new Runnable() {

			@Override
			public void run() {
				RecordingAspect.turnOff();
				System.out.println("Loading CUT class to avoid static init processing: " + new SomeClass());
				RecordingAspect.turnOn();

				SomeClass cut = new SomeClass();

				final int result = cut.getState();
				RecordingAspect.turnOff();
				System.out.println(result);
			}
		}, SUPERCLASS_CALL_RECORDING_SUBDIR);
	}
}
