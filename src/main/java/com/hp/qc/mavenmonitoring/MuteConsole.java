package com.hp.qc.mavenmonitoring;

import java.io.PrintStream;

//import org.apache.log4j.Logger;
import org.apache.maven.plugin.logging.Log;

public class MuteConsole {
	private static boolean muteConsoleOutput;

	private static PrintStream standartOutStream; 
	private static PrintStream standartErrStream;

	private MuteConsole()
	{
		
	}

	public static void setMutingMode(boolean muteConsoleOutput)
	{
		MuteConsole.muteConsoleOutput=muteConsoleOutput;
		tieSystemOutAndErrToLog();
	}
	
    private  static void tieSystemOutAndErrToLog() {
    	
    	standartOutStream=System.out;
    	standartErrStream=System.err;
        if(muteConsoleOutput)
        {
        	System.setOut(createLoggingProxy(System.out));
        	System.setErr(createLoggingProxy(System.err));
        }
    }

    private static PrintStream createLoggingProxy(final PrintStream realPrintStream) {
        return new PrintStream(realPrintStream) {
            public void print(final String string) {
                //Nothing to do here as we want to disable the logging
            }
        };
    }
    
    public static void revertMute()
    {
    	muteConsoleOutput=false;
    	System.setOut(standartOutStream);
    	System.setErr(standartErrStream);
    }
    
    
    
    
}
