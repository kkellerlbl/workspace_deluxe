package us.kbase.typedobj.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A thin container to return extracted Metadata (indicated by
 * the \@metadata ws annotation) during a pass over an object
 * stream via the MetadataExtractor.
 * @author msneddon
 * @author gaprice
 */
public class ExtractedMetadata {

	private JsonNode metadata;
	private static final ObjectMapper mapper = new ObjectMapper();
	/**
	 * The metadata JsonNode must be an object node with String type values for
	 * all fields.  We assume that this has already been validated earlier.
	 * @param wsSearchableSubset
	 * @param metadata
	 */
	public ExtractedMetadata(JsonNode metadata) {
		if(metadata == null) {
			this.metadata = mapper.createObjectNode();
		} else {
			this.metadata = metadata;
		}
	}
	
	public JsonNode getMetadata() {
		return metadata;
	}
	
	/** converts the data stored in the metadata JsonNode to a map */
	public Map<String,String> getMetadataAsMap() {
		Map <String,String> data = new HashMap<String,String>(metadata.size());
		Iterator<Entry<String, JsonNode>> ite = metadata.fields();
		while (ite.hasNext()) {
			Entry<String,JsonNode> next = ite.next();
			data.put(next.getKey(),next.getValue().asText());
		}
		return data;
	}
	
}
