package io.antmedia.datastore.db;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.datastore.db.types.ConferenceRoom;
import io.antmedia.datastore.db.types.Endpoint;
import io.antmedia.datastore.db.types.P2PConnection;
import io.antmedia.datastore.db.types.StreamInfo;
import io.antmedia.datastore.db.types.Subscriber;
import io.antmedia.datastore.db.types.TensorFlowObject;
import io.antmedia.datastore.db.types.Token;
import io.antmedia.datastore.db.types.VoD;
import io.antmedia.datastore.db.types.WebRTCViewerInfo;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.vertx.core.Vertx;


public class MapDBStore extends DataStore {

	private DB db;
	private BTreeMap<String, String> map;
	private BTreeMap<String, String> vodMap;
	private BTreeMap<String, String> detectionMap;
	private BTreeMap<String, String> tokenMap;
	private BTreeMap<String, String> subscriberMap;
	private BTreeMap<String, String> conferenceRoomMap;
	private BTreeMap<String, String> webRTCViewerMap;


	private Gson gson;
	private String dbName;
	private Iterable<String> dbFiles;
	private Vertx vertx;
	private long timerId;
	protected static Logger logger = LoggerFactory.getLogger(MapDBStore.class);
	private static final String MAP_NAME = "BROADCAST";
	private static final String VOD_MAP_NAME = "VOD";
	private static final String DETECTION_MAP_NAME = "DETECTION";
	private static final String TOKEN = "TOKEN";
	private static final String SUBSCRIBER = "SUBSCRIBER";
	private static final String SOCIAL_ENDPONT_CREDENTIALS_MAP_NAME = "SOCIAL_ENDPONT_CREDENTIALS_MAP_NAME";
	private static final String CONFERENCE_ROOM_MAP_NAME = "CONFERENCE_ROOM";
	private static final String WEBRTC_VIEWER = "WEBRTC_VIEWER";


	public MapDBStore(String dbName, Vertx vertx) {
		this.vertx = vertx;
		this.dbName = dbName;
		db = DBMaker
				.fileDB(dbName)
				.fileMmapEnableIfSupported()
				/*.transactionEnable() we disable this because under load, it causes exception.
					//In addition, we already commit and synch methods. So it seems that we don't need this one
				 */
				.checksumHeaderBypass()
				.make();


		map = db.treeMap(MAP_NAME).keySerializer(Serializer.STRING).valueSerializer(Serializer.STRING).counterEnable()
				.createOrOpen();

		vodMap = db.treeMap(VOD_MAP_NAME).keySerializer(Serializer.STRING).valueSerializer(Serializer.STRING)
				.counterEnable().createOrOpen();

		detectionMap = db.treeMap(DETECTION_MAP_NAME).keySerializer(Serializer.STRING)
				.valueSerializer(Serializer.STRING).counterEnable().createOrOpen();

		tokenMap = db.treeMap(TOKEN).keySerializer(Serializer.STRING).valueSerializer(Serializer.STRING)
				.counterEnable().createOrOpen();

		subscriberMap = db.treeMap(SUBSCRIBER).keySerializer(Serializer.STRING).valueSerializer(Serializer.STRING)
				.counterEnable().createOrOpen();

		conferenceRoomMap = db.treeMap(CONFERENCE_ROOM_MAP_NAME).keySerializer(Serializer.STRING).valueSerializer(Serializer.STRING)
				.counterEnable().createOrOpen();

		webRTCViewerMap = db.treeMap(WEBRTC_VIEWER).keySerializer(Serializer.STRING).valueSerializer(Serializer.STRING)
				.counterEnable().createOrOpen();

		GsonBuilder builder = new GsonBuilder();
		gson = builder.create();

		timerId = vertx.setPeriodic(5000, id -> 

		vertx.executeBlocking(b -> {

			synchronized (this) 
			{
				if (available) {
					db.commit();
				}
			}

		}, false, null)
				);

		available = true;
	}



	public BTreeMap<String, String> getVodMap() {
		return vodMap;
	}

	public void setVodMap(BTreeMap<String, String> vodMap) {
		this.vodMap = vodMap;
	}

	public BTreeMap<String, String> getMap() {
		return map;
	}

	public void setMap(BTreeMap<String, String> map) {
		this.map = map;
	}

	public BTreeMap<String, String> getDetectionMap() {
		return detectionMap;
	}

	public void setDetectionMap(BTreeMap<String, String> detectionMap) {
		this.detectionMap = detectionMap;
	}

	@Override
	public String save(Broadcast broadcast) {
		return super.save(null, map, null, null, broadcast, gson);
	}

	@Override
	public Broadcast get(String id) {
		return super.get(null, map, null, null, id, gson);
	}

	@Override
	public VoD getVoD(String id) {
		return super.getVoD(null, vodMap, null, null, id, gson);
	}

	@Override
	public boolean updateStatus(String id, String status) {
		return super.updateStatus(null, map, null, id, status, gson); 
	}

	@Override
	public boolean updateDuration(String id, long duration) {
		return super.updateDuration(null, map, null, id, duration, gson); 
	}

	@Override
	public boolean addEndpoint(String id, Endpoint endpoint) {
		return super.addEndpoint(null, map, null, id, endpoint, gson); 
	}

	@Override
	public boolean removeEndpoint(String id, Endpoint endpoint, boolean checkRTMPUrl) {
		return super.removeEndpoint(null, map, null, id, endpoint, checkRTMPUrl, gson); 
	}

	@Override
	public boolean removeAllEndpoints(String id) {
		return super.removeAllEndpoints(null, map, null, id, gson); 
	}


	/**
	 * Use getTotalBroadcastNumber
	 * @deprecated
	 */
	@Override
	@Deprecated
	public long getBroadcastCount() {
		synchronized (this) {
			return map.getSize();
		}
	}

	@Override
	public long getActiveBroadcastCount() {
		int activeBroadcastCount = 0;
		synchronized (this) {
			Collection<String> values = map.values();
			for (String broadcastString : values) {
				Broadcast broadcast = gson.fromJson(broadcastString, Broadcast.class);
				String status = broadcast.getStatus();
				if (status != null && status.equals(AntMediaApplicationAdapter.BROADCAST_STATUS_BROADCASTING)) {
					activeBroadcastCount++;
				}
			}
		}
		return activeBroadcastCount;
	}

	@Override
	public boolean delete(String id) {
		boolean result = false;
		synchronized (this) {
			result = map.remove(id) != null;
		}
		return result;
	}
	@Override
	public List<ConferenceRoom> getConferenceRoomList(int offset, int size, String sortBy, String orderBy, String search){
		return super.getConferenceRoomList(null, conferenceRoomMap, offset, size, sortBy, orderBy, search, gson);
	}

	//GetBroadcastList method may be called without offset and size to get the full list without offset or size
	//sortAndCrop method returns maximum 50 (hardcoded) of the broadcasts for an offset.
	public List<Broadcast> getBroadcastListV2(String type, String search) {
		return super.getBroadcastListV2(null, map, type, search, gson);
	}

	@Override
	public List<Broadcast> getBroadcastList(int offset, int size, String type, String sortBy, String orderBy, String search) {
		List<Broadcast> list = null;
		list = getBroadcastListV2(type ,search);
		return sortAndCropBroadcastList(list, offset, size, sortBy, orderBy);
	}

	public List<VoD> getVodListV2(String streamId, String search) {
		return super.getVodListV2(null, vodMap, streamId, search, gson, dbName);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<VoD> getVodList(int offset, int size, String sortBy, String orderBy, String streamId, String search) {
		List<VoD> vods = null;
		vods = getVodListV2(streamId,search);
		return sortAndCropVodList(vods, offset, size, sortBy, orderBy);
	}

	@Override
	public String addVod(VoD vod) {
		return super.addVod(null, vodMap, null, vod, gson);
	}


	@Override
	public List<Broadcast> getExternalStreamsList() {

		List<Broadcast> streamsList = new ArrayList<>();

		synchronized (this) {

			Object[] objectArray = map.getValues().toArray();
			Broadcast[] broadcastArray = new Broadcast[objectArray.length];


			for (int i = 0; i < objectArray.length; i++) {

				broadcastArray[i] = gson.fromJson((String) objectArray[i], Broadcast.class);

			}

			for (int i = 0; i < broadcastArray.length; i++) {
				String type = broadcastArray[i].getType();
				String status = broadcastArray[i].getStatus();

				if ((type.equals(AntMediaApplicationAdapter.IP_CAMERA) || type.equals(AntMediaApplicationAdapter.STREAM_SOURCE)) && (!status.equals(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING) && !status.equals(IAntMediaStreamHandler.BROADCAST_STATUS_PREPARING)) ) {
					streamsList.add(gson.fromJson((String) objectArray[i], Broadcast.class));
					broadcastArray[i].setStatus(IAntMediaStreamHandler.BROADCAST_STATUS_PREPARING);
					map.replace(broadcastArray[i].getStreamId(), gson.toJson(broadcastArray[i]));
				}
			}
		}
		return streamsList;
	}

	@Override
	public void close(boolean deleteDB) {
		//get db file before closing. They can be used in delete method
		dbFiles = db.getStore().getAllFiles();
		synchronized (this) {
			vertx.cancelTimer(timerId);
			db.commit();
			available = false;
			db.close();
		}

		if (deleteDB) 
		{
			for (String fileName : dbFiles) 
			{
				File file = new File(fileName);
				if (file.exists()) 
				{
					try {
						Files.delete(file.toPath());
					} catch (IOException e) {
						logger.error(ExceptionUtils.getStackTrace(e));
					}
				}
			}

		}
	}

	@Override
	public boolean deleteVod(String id) {
		return super.deleteVod(null, vodMap, id);
	}

	@Override
	public long getTotalVodNumber() {
		return super.getTotalVodNumber(null, vodMap);
	}

	@Override
	public long getPartialVodNumber(String search){
		List<VoD> vods = getVodListV2(null, search);
		return vods.size();
	}

	@Override
	public int fetchUserVodList(File userfile) {
		return super.fetchUserVodList(null,vodMap, userfile, gson, dbName);
	}


	@Override
	protected boolean updateSourceQualityParametersLocal(String id, String quality, double speed, int pendingPacketQueue) {
		boolean result = false;
		synchronized (this) {
			if (id != null) {
				String jsonString = map.get(id);
				if (jsonString != null) {
					Broadcast broadcast = gson.fromJson(jsonString, Broadcast.class);
					broadcast.setSpeed(speed);
					if (quality != null) {
						broadcast.setQuality(quality);
					}
					broadcast.setPendingPacketSize(pendingPacketQueue);
					map.replace(id, gson.toJson(broadcast));

					result = true;

				}
			}
		}
		return result;
	}

	@Override
	public long getTotalBroadcastNumber() {
		synchronized (this) {
			return map.size();
		}
	}

	@Override
	public long getPartialBroadcastNumber(String search) {
		return getBroadcastListV2(null ,search).size();
	}
	
	@Override
	public void saveDetection(String id, long timeElapsed, List<TensorFlowObject> detectedObjects) {
		super.saveDetection(null, detectionMap, id, timeElapsed, detectedObjects, gson);
	}

	@Override
	public List<TensorFlowObject> getDetection(String id) {

		synchronized (this) {
			if (id != null) {
				String jsonString = detectionMap.get(id);
				if (jsonString != null) {
					Type listType = new TypeToken<ArrayList<TensorFlowObject>>(){}.getType();
					return gson.fromJson(jsonString, listType);
				}
			}
		}
		return null;
	}

	@Override
	public List<TensorFlowObject> getDetectionList(String idFilter, int offsetSize, int batchSize) {
		return super.getDetectionList(null, detectionMap, idFilter, offsetSize, batchSize, gson);
	}

	@Override
	public long getObjectDetectedTotal(String id) {
		return super.getObjectDetectedTotal(null, detectionMap, id, gson);
	}


	/**
	 * Updates the stream's name, description, userName, password, IP address, stream URL if these values is not null
	 * @param streamId
	 * @param broadcast
	 * @return
	 */
	@Override
	public boolean updateBroadcastFields(String streamId, Broadcast broadcast) {
		return super.updateBroadcastFields(null, map, streamId, broadcast, gson);
	}

	@Override
	protected synchronized boolean updateHLSViewerCountLocal(String streamId, int diffCount) {
		return super.updateHLSViewerCountLocal(null, map, streamId, diffCount, gson);
	}
	
	@Override
	protected synchronized boolean updateDASHViewerCountLocal(String streamId, int diffCount) {
		return super.updateDASHViewerCountLocal(null, map, streamId, diffCount, gson);
	}

	@Override
	protected synchronized boolean updateWebRTCViewerCountLocal(String streamId, boolean increment) {
		return super.updateWebRTCViewerCountLocal(null, map, streamId, increment, gson);
	}

	@Override
	protected synchronized boolean updateRtmpViewerCountLocal(String streamId, boolean increment) {
		return super.updateRtmpViewerCountLocal(null, map, streamId, increment, gson);
	}

	@Override
	public void addStreamInfoList(List<StreamInfo> streamInfoList) {
		//used in mongo for cluster mode. useless here.
	}

	public void clearStreamInfoList(String streamId) {
		//used in mongo for cluster mode. useless here.
	}
	
	public List<StreamInfo> getStreamInfoList(String streamId) {
		return new ArrayList<>();
	}

	@Override
	public boolean saveToken(Token token) {
		return super.saveToken(null, tokenMap, token, gson);
	}

	@Override
	public Token validateToken(Token token) {
		return super.validateToken(null, tokenMap, token, gson);
	}

	@Override
	public boolean revokeTokens(String streamId) {
		return super.revokeTokens(null, tokenMap, streamId, gson);
	}

	@Override
	public List<Token> listAllTokens(String streamId, int offset, int size) {
		return super.listAllTokens(null, tokenMap, streamId, offset, size, gson);
	}

	@Override
	public List<Subscriber> listAllSubscribers(String streamId, int offset, int size) {
		return super.listAllSubscribers(null, subscriberMap, streamId, offset, size, gson);
	}

	@Override
	public boolean addSubscriber(String streamId, Subscriber subscriber) {
		return super.addSubscriber(null, subscriberMap, streamId, subscriber, gson);
	}

	@Override
	public boolean deleteSubscriber(String streamId, String subscriberId) {
		return super.deleteSubscriber(null, subscriberMap, streamId, subscriberId);
	}

	@Override
	public boolean revokeSubscribers(String streamId) {
		return super.revokeSubscribers(null, subscriberMap, streamId, gson);
	}

	@Override
	public Subscriber getSubscriber(String streamId, String subscriberId) {
		return super.getSubscriber(null, subscriberMap, streamId, subscriberId, gson);
	}		

	@Override
	public boolean resetSubscribersConnectedStatus() {
		return super.resetSubscribersConnectedStatus(null, subscriberMap, gson);
	}
	
	@Override
	public void saveStreamInfo(StreamInfo streamInfo) {
		//no need to implement this method, it is used in cluster mode
	}

	@Override
	public boolean setMp4Muxing(String streamId, int enabled) {
		return super.setMp4Muxing(null, map, streamId, enabled, gson);
	}

	@Override
	public boolean setWebMMuxing(String streamId, int enabled) {
		return super.setWebMMuxing(null, map, streamId, enabled, gson);
	}

	@Override
	public boolean createConferenceRoom(ConferenceRoom room) {
		return super.createConferenceRoom(null, conferenceRoomMap, room, gson);
	}

	@Override
	public boolean editConferenceRoom(String roomId, ConferenceRoom room) {
		return super.editConferenceRoom(null, conferenceRoomMap, roomId, room, gson);
	}

	@Override
	public boolean deleteConferenceRoom(String roomId) {
		return super.deleteConferenceRoom(null, conferenceRoomMap, roomId);
	}

	@Override
	public ConferenceRoom getConferenceRoom(String roomId) {
		return super.getConferenceRoom(null, conferenceRoomMap, roomId, gson);
	}

	@Override
	public boolean deleteToken(String tokenId) {
		return super.deleteToken(null, tokenMap, tokenId);
	}

	@Override
	public Token getToken(String tokenId) {
		return super.getToken(null, tokenMap, tokenId, gson);
	}	

	@Override
	public boolean createP2PConnection(P2PConnection conn) {
		// No need to implement. It used in cluster mode
		return false;
	}

	@Override
	public boolean deleteP2PConnection(String streamId) {
		// No need to implement. It used in cluster mode
		return false;
	}

	@Override
	public P2PConnection getP2PConnection(String streamId) {
		// No need to implement. It used in cluster mode
		return null;
	}

	@Override
	public boolean addSubTrack(String mainTrackId, String subTrackId) {
		boolean result = false;
		synchronized (this) {
			String json = map.get(mainTrackId);
			Broadcast mainTrack = gson.fromJson(json, Broadcast.class);
			List<String> subTracks = mainTrack.getSubTrackStreamIds();
			if (subTracks == null) {
				subTracks = new ArrayList<>();
			}
			subTracks.add(subTrackId);
			mainTrack.setSubTrackStreamIds(subTracks);
			map.replace(mainTrackId, gson.toJson(mainTrack));
			result = true;
		}

		return result;
	}

	@Override
	public int resetBroadcasts(String hostAddress) 
	{
		synchronized (this) {

			Collection<String> broadcastsRawJSON = map.values();
			int size = broadcastsRawJSON.size();
			int updateOperations = 0;
			int zombieStreamCount = 0;
			int i = 0;
			for (String broadcastRaw : broadcastsRawJSON) {
				i++;
				if (broadcastRaw != null) {
					Broadcast broadcast = gson.fromJson(broadcastRaw, Broadcast.class);
					if (broadcast.isZombi()) {
						zombieStreamCount++;
						map.remove(broadcast.getStreamId());
					}
					else {
						updateOperations++;
						broadcast.setHlsViewerCount(0);
						broadcast.setWebRTCViewerCount(0);
						broadcast.setRtmpViewerCount(0);
						broadcast.setStatus(AntMediaApplicationAdapter.BROADCAST_STATUS_FINISHED);
						map.put(broadcast.getStreamId(), gson.toJson(broadcast));
					}
				}

				if (i > size) {
					logger.error("Inconsistency in DB found in resetting broadcasts. It's likely db file({}) is damaged", dbName);
					break;
				}
			}
			logger.info("Reset broadcasts result in deleting {} zombi streams and {} update operations", zombieStreamCount, updateOperations );

			return updateOperations + zombieStreamCount;
		}
	}

	@Override
	public int getTotalWebRTCViewersCount() {
		long now = System.currentTimeMillis();
		if(now - totalWebRTCViewerCountLastUpdateTime > TOTAL_WEBRTC_VIEWER_COUNT_CACHE_TIME) {
			int total = 0;
			synchronized (this) {
				for (String json : map.getValues()) {
					Broadcast broadcast = gson.fromJson(json, Broadcast.class);
					total += broadcast.getWebRTCViewerCount();
				}
			}
			totalWebRTCViewerCount = total;
			totalWebRTCViewerCountLastUpdateTime = now;
		}  
		return totalWebRTCViewerCount;
	}

	@Override
	public void saveViewerInfo(WebRTCViewerInfo info) {
		synchronized (this) {
			if (info != null) {
				try {
					webRTCViewerMap.put(info.getViewerId(), gson.toJson(info));
				} catch (Exception e) {
					logger.error(ExceptionUtils.getStackTrace(e));
				}
			}
		}
	}



	@Override
	public List<WebRTCViewerInfo> getWebRTCViewerList(int offset, int size, String sortBy, String orderBy,
			String search) {

		ArrayList<WebRTCViewerInfo> list = new ArrayList<>();
		synchronized (this) {
			Collection<String> webRTCViewers = webRTCViewerMap.getValues();
			for (String infoString : webRTCViewers)
			{
				WebRTCViewerInfo info = gson.fromJson(infoString, WebRTCViewerInfo.class);
				list.add(info);
			}
		}
		if(search != null && !search.isEmpty()){
			logger.info("server side search called for Conference Room = {}", search);
			list = searchOnWebRTCViewerInfo(list, search);
		}
		return sortAndCropWebRTCViewerInfoList(list, offset, size, sortBy, orderBy);
	}



	@Override
	public boolean deleteWebRTCViewerInfo(String viewerId) {
		synchronized (this) 
		{		
			return webRTCViewerMap.remove(viewerId) != null;
		}
	}
	
	@Override
	public boolean updateStreamMetaData(String streamId, String metaData) {
		boolean result = false;
		synchronized (this) {
			if (streamId != null) {
				String jsonString = map.get(streamId);
				if (jsonString != null) {
					Broadcast broadcast = gson.fromJson(jsonString, Broadcast.class);
					broadcast.setMetaData(metaData);
					String jsonVal = gson.toJson(broadcast);
					String previousValue = map.replace(streamId, jsonVal);
					result = true;
					logger.debug("updateStatus replacing id {} having value {} to {}", streamId, previousValue, jsonVal);
				}
			}
		}
		return result;
	}

}
