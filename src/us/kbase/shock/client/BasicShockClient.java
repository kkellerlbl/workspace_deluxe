package us.kbase.shock.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;
import us.kbase.auth.TokenExpiredException;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNodeDeletedException;
import us.kbase.shock.client.exceptions.UnvalidatedEmailException;

/**
 * A basic client for shock. Creating nodes, deleting nodes,
 * getting a subset of node data, and altering read acls is currently supported.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class BasicShockClient {
	
	private final URI baseurl;
	private final URI nodeurl;
	private final HttpClient client = new DefaultHttpClient();
	private final ObjectMapper mapper = new ObjectMapper();
	private AuthToken token = null;
	
	private static final String AUTH = "Authorization";
	private static final String OAUTH = "OAuth ";
	private static final String DOWNLOAD = "/?download";
	private static final String ATTRIBFILE = "attribs";
	private static final ShockACLType ACL_READ = new ShockACLType("read");
	
	private static final int CHUNK_SIZE = 100000000; //~100 Mb
	
	/**
	 * Create a new shock client authorized to act as a shock user.
	 * @param url the location of the shock server.
	 * @param token the authorization token to present to shock.
	 * @throws IOException if an IO problem occurs.
	 * @throws InvalidShockUrlException if the <code>url</code> does not reference
	 * a shock server.
	 * @throws TokenExpiredException if the <code>token</code> is expired.
	 */
	public BasicShockClient(final URL url, final AuthToken token)
			throws IOException, InvalidShockUrlException,
			TokenExpiredException {
		this(url);
		updateToken(token);
	}
	
	/**
	 * Create a new shock client.
	 * @param url the location of the shock server.
	 * @throws IOException if an IO problem occurs.
	 * @throws InvalidShockUrlException if the <code>url</code> does not reference
	 * a shock server.
	 */
	@SuppressWarnings("unchecked")
	public BasicShockClient(final URL url) throws IOException, 
			InvalidShockUrlException {

		mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
		
		String turl = url.getProtocol() + "://" + url.getAuthority()
				+ url.getPath();
		if (turl.charAt(turl.length() - 1) != '/') {
			turl = turl + "/";
		}
		if (!(url.getProtocol().equals("http") ||
				url.getProtocol().equals("https"))) {
			throw new InvalidShockUrlException(turl.toString());
			
		}
		final HttpResponse response = client.execute(new HttpGet(turl));
		final String resp = EntityUtils.toString(response.getEntity());
		final Map<String, Object> shockresp;
		try {
			shockresp = mapper.readValue(resp, Map.class);
		} catch (JsonParseException jpe) {
			throw new InvalidShockUrlException(turl.toString());
		}
		if (!shockresp.containsKey("id")) {
			throw new InvalidShockUrlException(turl.toString());
		}
		if (!shockresp.get("id").equals("Shock")) {
			throw new InvalidShockUrlException(turl.toString());
		}
		URL shockurl = new URL(shockresp.get("url").toString());
		//https->http is caused by the router, not shock, per Jared
		if (url.getProtocol().equals("https")) {
			shockurl = new URL("https", shockurl.getAuthority(),
					shockurl.getPort(), shockurl.getFile());
		}
		try {
			baseurl = shockurl.toURI();
		} catch (URISyntaxException use) {
			throw new Error(use); //something went badly wrong 
		}
		nodeurl = baseurl.resolve("node/");
	}
	
	/**
	 * Replace the token this client presents to the shock server.
	 * @param token the new token
	 * @throws TokenExpiredException if the <code>token</code> is expired.
	 */
	public void updateToken(final AuthToken token)
			throws TokenExpiredException {
		if (token == null) {
			this.token = null;
			return;
		}
		if (token.isExpired()) {
			throw new TokenExpiredException(token.getTokenId());
		}
		this.token = token;
	}
	
	/**
	 * Check the token's validity.
	 * @return <code>true</code> if the client has no auth token or the token
	 * is expired, <code>false</code> otherwise.
	 */
	public boolean isTokenExpired() {
		if (token == null || token.isExpired()) {
			return true;
		}
		return false;
	}
	
	/** 
	 * Get the url of the shock server this client communicates with.
	 * @return the shock url.
	 */
	public URL getShockUrl() {
		return uriToUrl(baseurl);
	}
	
	private <T extends ShockResponse> ShockData
			processRequest(final HttpRequestBase httpreq, final Class<T> clazz)
			throws IOException, ShockHttpException, TokenExpiredException {
		authorize(httpreq);
		final HttpResponse response = client.execute(httpreq);
		return getShockData(response, clazz);
	}
	
	private <T extends ShockResponse> ShockData
			getShockData(final HttpResponse response, final Class<T> clazz) 
			throws IOException, ShockHttpException {
		final String resp = EntityUtils.toString(response.getEntity());
		try {
			return mapper.readValue(resp, clazz).getShockData();
		} catch (JsonParseException jpe) {
			throw new Error(jpe); //something's broken
		}
	}
	
	private void authorize(final HttpRequestBase httpreq) throws
			TokenExpiredException {
		if (token != null) {
			if (token.isExpired()) {
				throw new TokenExpiredException(token.getTokenId());
			}
			httpreq.setHeader(AUTH, OAUTH + token);
		}
	}

	/** 
	 * Gets a node from the shock server. Note the object returned 
	 * represents the shock node's state at the time getNode() was called
	 * and does not update further.
	 * @param id the ID of the shock node.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be fetched from shock.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode getNode(final ShockNodeId id) throws IOException,
			ShockHttpException, TokenExpiredException {
		final URI targeturl = nodeurl.resolve(id.getId());
		final HttpGet htg = new HttpGet(targeturl);
		final ShockNode sn = (ShockNode)processRequest(htg, ShockNodeResponse.class);
		sn.addClient(this);
		return sn;
	}
	
	/**
	 * Get a file from a shock node.
	 * @param id the ID of the shock node.
	 * @return the contents of the file.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the file could not be fetched from shock.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public byte[] getFile(final ShockNodeId id) throws IOException,
			ShockHttpException, TokenExpiredException {
		if (id == null) {
			throw new IllegalArgumentException("id may not be null");
		}
		final URI targeturl = nodeurl.resolve(id.getId() + DOWNLOAD);
		final HttpGet htg = new HttpGet(targeturl);
		authorize(htg);
		final HttpResponse response = client.execute(htg);
		final int code = response.getStatusLine().getStatusCode();
		if (code > 299) {
			getShockData(response, ShockNodeResponse.class); //trigger errors
		}//TODO return InputStream
		return EntityUtils.toByteArray(response.getEntity());
	}
	
	/**
	 * Creates an empty node on the shock server.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode() throws IOException, ShockHttpException,
			TokenExpiredException {
		return _addNode(null, null, null);
	}
	
	/**
	 * Creates an node on the shock server with user-specified attributes.
	 * @param attributes the user-specified attributes.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws JsonProcessingException if the <code>attributes</code> could
	 * not be serialized to JSON.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode(final Map<String, Object> attributes) throws
			IOException, ShockHttpException, JsonProcessingException,
			TokenExpiredException {
		if (attributes == null) {
			throw new IllegalArgumentException("attributes may not be null");
		}
		return _addNode(attributes, null, null);
	}
	
	//TODO drop all byte[] based methods, make them InputStream based
	/**
	 * Creates a node on the shock server containing a file.
	 * @param file the file data.
	 * @param filesize the length of the file. If the file length is not
	 * accurate, an error will be thrown.
	 * @param filename the name of the file.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode(final InputStream file, final long filesize,
			final String filename)
			throws IOException, ShockHttpException, TokenExpiredException {
		if (file == null) {
			throw new IllegalArgumentException("file may not be null");
		}
		if (filesize < 1) {
			throw new IllegalArgumentException("filesize must be > 0");
		}
		if (filename == null) {
			throw new IllegalArgumentException("filename may not be null");
		}
		return _addNode(null, file, filesize, filename);
	}
	
	/**
	 * Creates an node on the shock server with user-specified attributes and 
	 * a file.
	 * @param attributes the user-specified attributes.
	 * @param file the file data.
	 * @param filesize the length of the file. If the file length is not
	 * accurate, an error will be thrown.
	 * @param filename the name of the file.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws JsonProcessingException if the <code>attributes</code> could
	 * not be serialized to JSON.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode(final Map<String, Object> attributes,
			final InputStream file, final long filesize, final String filename)
			throws IOException, ShockHttpException,
			JsonProcessingException, TokenExpiredException {
		if (attributes == null) {
			throw new IllegalArgumentException("attributes may not be null");
		}
		if (file == null) {
			throw new IllegalArgumentException("file may not be null");
		}
		if (filesize < 1) {
			throw new IllegalArgumentException("filesize must be > 0");
		}
		if (filename == null) {
			throw new IllegalArgumentException("filename may not be null");
		}
		return _addNode(attributes, file, filesize, filename);
	}
	
	private ShockNode _addNode(final Map<String, Object> attributes,
			final byte[] file, final String filename)
			throws IOException, ShockHttpException, JsonProcessingException,
			TokenExpiredException {
		final HttpPost htp = new HttpPost(nodeurl);
		if (attributes != null || file != null) {
			final MultipartEntity mpe = new MultipartEntity();
			if (attributes != null) {
				final byte[] attribs = mapper.writeValueAsBytes(attributes);
				mpe.addPart("attributes", new ByteArrayBody(attribs, ATTRIBFILE));
			}
			if (file != null) {
				mpe.addPart("upload", new ByteArrayBody(file, filename));
			}
			htp.setEntity(mpe);
		}
		final ShockNode sn = (ShockNode) processRequest(htp,
				ShockNodeResponse.class);
		sn.addClient(this);
		return sn;
	}
	
	private ShockNode _addNode(final Map<String, Object> attributes,
			final InputStream file, final Long filesize, final String filename)
			throws IOException, ShockHttpException, JsonProcessingException,
			TokenExpiredException {
		//TODO test
		if (file == null) {
			return _addNode(attributes, null, null);
		}
		if (filesize <= CHUNK_SIZE) {
			final byte[] b = new byte[(int) filesize.longValue()];
			final int read = file.read(b);
			if (read < 1) {
				throw new IllegalArgumentException("No data provided");
			}
			if (read != filesize) {
				throw new IllegalArgumentException("Incorrect file size: "
						+ filesize);
			}
			final byte[] foo = new byte[1];
			if (file.read(foo) > 0) {
				throw new IllegalArgumentException("Incorrect file size: "
						+ filesize);
			}
			return _addNode(attributes, b, filename);
		}
		final HttpPost htp = new HttpPost(nodeurl);
		int chunks = new Double(Math.ceil((float) filesize) /
				CHUNK_SIZE).intValue();
		if (attributes != null) {
			final MultipartEntity mpe = new MultipartEntity();
			if (attributes != null) {
				final byte[] attribs = mapper.writeValueAsBytes(attributes);
				mpe.addPart("attributes", new ByteArrayBody(attribs, ATTRIBFILE));
			}
			mpe.addPart("parts", new StringBody("" + chunks));
			htp.setEntity(mpe);
		}
		final ShockNode sn = (ShockNode) processRequest(htp,
				ShockNodeResponse.class);
		sn.addClient(this);
		
		final URI targeturl = nodeurl.resolve(getIdIgnoreException(sn).getId());
		for (int i = 0; i < chunks; i++) {
			final HttpPost partpost = new HttpPost(targeturl);
			final byte[] b = new byte[CHUNK_SIZE]; //can this be moved outside the loop safely?
			if (file.read(b) < 1) {
				deleteNode(getIdIgnoreException(sn));
				throw new IllegalArgumentException(
						"reached EOF prior to filesize of " + filesize);
			}
			final MultipartEntity mpe = new MultipartEntity();
			mpe.addPart("" + (i + 1), new ByteArrayBody(b, filename));
			partpost.setEntity(mpe);
			processRequest(partpost, ShockNodeResponse.class);
		}
		final byte[] b = new byte[1];
		if (file.read(b) > 0) {
			deleteNode(getIdIgnoreException(sn));
			throw new IllegalArgumentException(
					"reached EOF prior to filesize of " + filesize);
		}
		return sn;
	}
	
	private ShockNodeId getIdIgnoreException(final ShockNode node) {
		try {
			return node.getId();
		} catch (ShockNodeDeletedException snde) {
			throw new RuntimeException("Node is in an incorrect deleted state");
		}
	}
		
	
	
	/**
	 * Deletes a node on the shock server.
	 * @param id the node to delete.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be deleted.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public void deleteNode(final ShockNodeId id) throws IOException, 
			ShockHttpException, TokenExpiredException {
		final URI targeturl = nodeurl.resolve(id.getId());
		final HttpDelete htd = new HttpDelete(targeturl);
		processRequest(htd, ShockNodeResponse.class); //triggers throwing errors
	}
	
	/**
	 * Adds a user to a shock node's read access control list (ACL).
	 * @param id the node to modify.
	 * @param user the user to be added to the read ACL.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node ACL could not be altered.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 * @throws UnvalidatedEmailException if the <code>user</code>'s email
	 * address is unvalidated.
	 */
	public void setNodeReadable(final ShockNodeId id, final AuthUser user)
			throws IOException, ShockHttpException,TokenExpiredException,
			UnvalidatedEmailException {
		if (!user.isEmailValidated()) {
			throw new UnvalidatedEmailException(String.format(
					"User %s's email address is not validated",
					user.getUserId()));
		}
		final URI targeturl = nodeurl.resolve(id.getId() + ACL_READ.acl + 
				"?users=" + user.getEmail()); //TODO WAIT DEP use userid when shock allows
		final HttpPut htp = new HttpPut(targeturl);
		processRequest(htp, ShockACLResponse.class); //triggers throwing errors
	}
	
	/**
	 * Makes a node world readable.
	 * @param id the node to modify.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node read access control list could not be
	 * altered.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public void setNodeWorldReadable(final ShockNodeId id) throws IOException,
			ShockHttpException, TokenExpiredException {
		final List<ShockUserId> acls = getACLs(id, ACL_READ).getRead();
		final List<String> userlist = new ArrayList<String>();
		for (ShockUserId uid: acls) {
			userlist.add(uid.getId());
		}
		final URI targeturl = nodeurl.resolve(id.getId() + ACL_READ.acl +
				"?users=" + StringUtils.join(userlist, ","));
		final HttpDelete htd = new HttpDelete(targeturl);
		processRequest(htd, ShockACLResponse.class);
		
	}
	
	/**
	 * Retrieves all the access control lists (ACLs) from the shock server for
	 * a node. Note the object returned represents the shock node's state at
	 * the time getACLs() was called and does not update further.
	 * @param id the node to query.
	 * @return the ACLs for the node.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node's access control lists could not be
	 * retrieved.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockACL getACLs(final ShockNodeId id) throws IOException,
			ShockHttpException, TokenExpiredException {
		return getACLs(id, new ShockACLType());
	}
	
	/**
	 * Retrieves a specific access control list (ACL) from the shock server for
	 * a node. Note the object returned represents the shock node's state at
	 * the time getACLs() was called and does not update further.
	 * @param id the node to query.
	 * @param acl the type of ACL to retrieve.
	 * @return the ACL for the node.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node's access control list could not be
	 * retrieved.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockACL getACLs(final ShockNodeId id, final ShockACLType acl) 
			throws IOException, ShockHttpException, TokenExpiredException {
		final URI targeturl = nodeurl.resolve(id.getId() + acl.acl);
		final HttpGet htg = new HttpGet(targeturl);
		return (ShockACL)processRequest(htg, ShockACLResponse.class);
	}
	
	//for known good uris ONLY
	private URL uriToUrl(final URI uri) {
		try {
			return uri.toURL();
		} catch (MalformedURLException mue) {
			throw new RuntimeException(mue); //something is seriously fuxxored
		}
	}
}
