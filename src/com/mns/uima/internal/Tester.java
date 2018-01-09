package com.mns.uima.internal;

import com.mns.uima.FactExtractCloud;


public class Tester {
	public static void main(String[] args) {
		String fileType1 = "/Users/martin/Documents/Development/Java/Annotators/FactExtractCloud/data/cloudant-ts.xml";  // _InitialView
		String CASFile1 = "/Users/martin/Documents/Development/Java/Annotators/FactExtractCloud/data/cloudant.xmi";

		FactExtractCloud anno = new FactExtractCloud();
		anno.setpAccount("5f0e9d4f-a4f8-4615-b0e6-274b7a964609-bluemix");
		anno.setpUsername("5f0e9d4f-a4f8-4615-b0e6-274b7a964609-bluemix");
		anno.setpPassword("e51f0856e35277b51c19b84fdd244b5bc35331a7c99d59e1c4b7a88fa2ced667");
		anno.setpDatabase("extract");
		anno.setpTriggerFeature("persist");
		
		anno.TestAnnotator( fileType1, CASFile1);

	}
}
