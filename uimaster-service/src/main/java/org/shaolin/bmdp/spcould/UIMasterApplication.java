package org.shaolin.bmdp.spcould;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.shaolin.bmdp.datamodel.common.DiagramType;
import org.shaolin.bmdp.datamodel.page.UIEntity;
import org.shaolin.bmdp.datamodel.page.UIPage;
import org.shaolin.bmdp.datamodel.page.WebService;
import org.shaolin.bmdp.datamodel.pagediagram.WebChunk;
import org.shaolin.bmdp.persistence.HibernateUtil;
import org.shaolin.bmdp.runtime.AppContext;
import org.shaolin.bmdp.runtime.Registry;
import org.shaolin.bmdp.runtime.cache.CacheManager;
import org.shaolin.bmdp.runtime.cache.ICache;
import org.shaolin.bmdp.runtime.ce.ConstantServiceImpl;
import org.shaolin.bmdp.runtime.entity.EntityAddedEvent;
import org.shaolin.bmdp.runtime.entity.EntityManager;
import org.shaolin.bmdp.runtime.entity.EntityUpdatedEvent;
import org.shaolin.bmdp.runtime.entity.IEntityEventListener;
import org.shaolin.bmdp.runtime.internal.AppServiceManagerImpl;
import org.shaolin.bmdp.runtime.spi.IAppServiceManager.State;
import org.shaolin.bmdp.runtime.spi.IEntityManager;
import org.shaolin.bmdp.runtime.spi.IServerServiceManager;
import org.shaolin.javacc.StatementParser;
import org.shaolin.javacc.context.OOEEContext;
import org.shaolin.javacc.context.OOEEContextFactory;
import org.shaolin.javacc.exception.ParsingException;
import org.shaolin.javacc.statement.CompilationUnit;
import org.shaolin.uimaster.page.cache.PageCacheManager;
import org.shaolin.uimaster.page.cache.UIFlowCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class UIMasterApplication {
	private static final Logger logger = LoggerFactory.getLogger(UIMasterApplication.class);
	
	public static void main(String[] args) {
		Registry registry = Registry.getInstance();
		registry.initRegistry();

		List<String> cacheItems = registry.getNodeChildren("/System/caches");
		for (String cacheName : cacheItems) {
			Map<String, String> config = registry.getNodeItems("/System/caches/" + cacheName);
			String maxSizeStr = config.get("maxSize");
			String minutesStr = config.get("refreshTimeInMinutes");
			String description = config.get("description");
			int maxSize;
			long minutes;
			try {
				maxSize = Integer.parseInt(maxSizeStr);
			} catch (NumberFormatException e) {
				maxSize = -1;
				logger.warn("maxSize format error, now use the default -1");
			}
			try {
				minutes = Long.parseLong(minutesStr);
			} catch (NumberFormatException e) {
				minutes = -1;
				logger.warn("refresh interval error, now use the default -1");
			}
			ICache<String, ConcurrentHashMap> cache = CacheManager.getInstance().getCache(cacheName, maxSize, false,
					String.class, ConcurrentHashMap.class);
			cache.setRefreshInterval(minutes);
			cache.setDescription(description);
		}
		// load all entities from applications. only load once if there were
		IEntityManager entityManager = IServerServiceManager.INSTANCE.getEntityManager();
		addEntityListeners(entityManager);
		((EntityManager) entityManager).initRuntime();

		logger.info("Initializing UIMaster application instance ...");
		IServerServiceManager serverManager = IServerServiceManager.INSTANCE;
		String appName = "UIMaster";
		AppServiceManagerImpl appServiceManager = new AppServiceManagerImpl(appName, UIMasterApplication.class.getClassLoader());
		try {
			AppContext.register(appServiceManager);
			// add application to the server manager.
			serverManager.addApplication(appName, appServiceManager);
			
			// initialize DB.
	    	HibernateUtil.getSession();
			
			//load all customized constant items from DB table.
			entityManager.addEventListener((ConstantServiceImpl)appServiceManager.getConstantService());
			//load all customized workflow from DB table in WorkflowLifecycleServiceImpl.
			
	    	// wire all services.
	    	OOEEContext context = OOEEContextFactory.createOOEEContext();
	    	List<String> serviceNodes = Registry.getInstance().getNodeChildren("/System/services");
        	for (String path: serviceNodes) {
        		String expression = Registry.getInstance().getExpression("/System/services/" + path);
        		logger.debug("Evaluate module initial expression: " + expression);
        		CompilationUnit compliedUnit = StatementParser.parse(expression, context);
        		compliedUnit.execute(context);
        		
        	}
        	appServiceManager.startLifeCycleProviders();
        	logger.info(appName + " is ready for request.");
	    	
        	appServiceManager.setState(State.ACTIVE);
        	HibernateUtil.releaseSession(HibernateUtil.getSession(), true);
        	
        	//entityManager.offUselessCaches();
		} catch (Throwable e) {
			logger.error("Fails to start Config server start! Error: " + e.getMessage(), e);
			//appServiceManager.setState(State.FAILURE);
			//HibernateUtil.releaseSession(HibernateUtil.getSession(), false);
		} 
		
		SpringApplication.run(UIMasterApplication.class, args);
	}
	
	static void addEntityListeners(IEntityManager entityManager) {
		
		entityManager.addEventListener(new IEntityEventListener<WebChunk, DiagramType>() {
			@Override
			public void setEntityManager(EntityManager entityManager) {
			}

			@Override
			public void notify(
					EntityAddedEvent<WebChunk, DiagramType> event) {
				try {
					UIFlowCacheManager.getInstance().addChunk(
							event.getEntity());
				} catch (ParsingException e) {
					logger.error(
							"Parse ui flow error: " + e.getMessage(), e);
				}
			}

			@Override
			public void notify(
					EntityUpdatedEvent<WebChunk, DiagramType> event) {
				try {
					UIFlowCacheManager.getInstance().addChunk(
							event.getNewEntity());
				} catch (ParsingException e) {
					logger.error(
							"Parse ui flow error: " + e.getMessage(), e);
				}
			}

			@Override
			public void notifyLoadFinish(DiagramType diagram) {
			}

			@Override
			public void notifyAllLoadFinish() {
			}

			@Override
			public Class<WebChunk> getEventType() {
				return WebChunk.class;
			}

		});
		entityManager.addEventListener(new IEntityEventListener<UIPage, DiagramType>() {
			ArrayList<String> uipages = new ArrayList<String>();
			@Override
			public void setEntityManager(EntityManager entityManager) {
			}

			@Override
			public void notify(
					EntityAddedEvent<UIPage, DiagramType> event) {
				uipages.add(event.getEntity().getEntityName());
			}

			@Override
			public void notify(
					EntityUpdatedEvent<UIPage, DiagramType> event) {
				uipages.add(event.getNewEntity().getEntityName());
			}

			@Override
			public void notifyLoadFinish(DiagramType diagram) {
			}

			@Override
			public void notifyAllLoadFinish() {
				for (String uipage: uipages) {
					PageCacheManager.removeUIPageCache(uipage);
					try {
						PageCacheManager.getODPageEntityObject(uipage);
						PageCacheManager.getUIPageObject(uipage);
					} catch (Exception e) {
						logger.error(
								"Parse ui page error: " + e.getMessage(), e);
					}
				}
				uipages.clear();
			}

			@Override
			public Class<UIPage> getEventType() {
				return UIPage.class;
			}
		});
		entityManager.addEventListener(new IEntityEventListener<UIEntity, DiagramType>() {
			ArrayList<String> uiforms = new ArrayList<String>();
			@Override
			public void setEntityManager(EntityManager entityManager) {
			}

			@Override
			public void notify(
					EntityAddedEvent<UIEntity, DiagramType> event) {
				uiforms.add(event.getEntity().getEntityName());
			}

			@Override
			public void notify(
					EntityUpdatedEvent<UIEntity, DiagramType> event) {
				uiforms.add(event.getNewEntity().getEntityName());
			}

			@Override
			public void notifyLoadFinish(DiagramType diagram) {
			}

			@Override
			public void notifyAllLoadFinish() {
				for (String uiform: uiforms) {
					PageCacheManager.removeUIFormCache(uiform);
					try {
						PageCacheManager.getODFormObject(uiform);
						PageCacheManager.getUIFormObject(uiform);
					} catch (Exception e) {
						logger.error(
								"Parse ui page error: " + e.getMessage(), e);
					}
				}
				uiforms.clear();
			}

			@Override
			public Class<UIEntity> getEventType() {
				return UIEntity.class;
			}
		});
		entityManager.addEventListener(new IEntityEventListener<WebService, DiagramType>() {
			@Override
			public void setEntityManager(EntityManager entityManager) {
			}

			@Override
			public void notify(
					EntityAddedEvent<WebService, DiagramType> event) {
				try {
					PageCacheManager.addWebService(event.getEntity());
				} catch (ParsingException e) {
					logger.error(
							"Parse web service error: " + e.getMessage(), e);
				}
			}

			@Override
			public void notify(
					EntityUpdatedEvent<WebService, DiagramType> event) {
				try {
					PageCacheManager.addWebService(event.getNewEntity());
				} catch (ParsingException e) {
					logger.error(
							"Parse web service error: " + e.getMessage(), e);
				}
			}

			@Override
			public void notifyLoadFinish(DiagramType diagram) {
			}

			@Override
			public void notifyAllLoadFinish() {
			}

			@Override
			public Class<WebService> getEventType() {
				return WebService.class;
			}
		});
	}

}
