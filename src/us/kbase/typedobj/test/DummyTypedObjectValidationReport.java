package us.kbase.typedobj.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.idref.IdReferenceSet;

/**
 * for testing, you can instantiate this report without running any validation code.
 * this dummy report always says that it was a success, that no subset data is extracted (get an empty object), there
 * are no errors, no IDs detected, and no Ids that can be renamed.
 * @author msneddon
 *
 */
public class DummyTypedObjectValidationReport extends
		TypedObjectValidationReport {
	
	public DummyTypedObjectValidationReport(final AbsoluteTypeDefId type, 
			final UObject data) {
		super(Collections.<String>emptyList(), null, type, data, null, new IdReferenceSet().lock());
	}
	
	@Override
	public boolean isInstanceValid() {
		return true;
	}
	
	@Override
	public List <String> getErrorMessages() {
		return new ArrayList<String>();
	}
	
	@Override
	public IdReferenceSet getIdReferences() {
		return new IdReferenceSet().lock();
	}
	
	@Override
	public JsonNode getInstanceAfterIdRefRelabelingForTests() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.createObjectNode();
	}

	/*@Override
	public JsonNode getJsonInstance() {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.createObjectNode();
	}*/
	
	public JsonNode extractSearchableWsSubset() {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.createObjectNode();
	}
	
	@Override
	public String toString() { 
		return "DummyTypedObjectValidationReport";
	}
	
	
	
}
