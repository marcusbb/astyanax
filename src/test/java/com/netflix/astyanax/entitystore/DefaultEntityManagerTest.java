package com.netflix.astyanax.entitystore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Random;

import javax.persistence.PersistenceException;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolType;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.astyanax.entitystore.NullableEntity.AllMandatoryNestedEntity;
import com.netflix.astyanax.entitystore.NullableEntity.AllOptionalNestedEntity;
import com.netflix.astyanax.entitystore.SampleEntity.Bar;
import com.netflix.astyanax.entitystore.SampleEntity.Bar.BarBar;
import com.netflix.astyanax.entitystore.SampleEntity.Foo;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;
import com.netflix.astyanax.util.SingletonEmbeddedCassandra;
import com.netflix.astyanax.util.TimeUUIDUtils;

public class DefaultEntityManagerTest {

	private static Keyspace                  keyspace;
	private static AstyanaxContext<Keyspace> keyspaceContext;

	private static String TEST_CLUSTER_NAME  = "junit_cass_sandbox";
	private static String TEST_KEYSPACE_NAME = "EntityPersisterTestKeyspace";
	private static final String SEEDS = "localhost:9160";

	public static ColumnFamily<String, String> CF_SAMPLE_ENTITY = ColumnFamily.newColumnFamily(
			"sampleentity", 
			StringSerializer.get(),
			StringSerializer.get());

	public static ColumnFamily<String, String> CF_SIMPLE_ENTITY = ColumnFamily.newColumnFamily(
			"simpleentity", 
			StringSerializer.get(),
			StringSerializer.get());

	@BeforeClass
	public static void setup() throws Exception {

		SingletonEmbeddedCassandra.getInstance();

		Thread.sleep(1000 * 3);

		createKeyspace();

		Thread.sleep(1000 * 3);
		
	}

	@AfterClass
	public static void teardown() throws Exception {
		if (keyspaceContext != null)
			keyspaceContext.shutdown();

		Thread.sleep(1000 * 10);
	}

	private static void createKeyspace() throws Exception {
		keyspaceContext = new AstyanaxContext.Builder()
		.forCluster(TEST_CLUSTER_NAME)
		.forKeyspace(TEST_KEYSPACE_NAME)
		.withAstyanaxConfiguration(
				new AstyanaxConfigurationImpl()
				.setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE)
				.setConnectionPoolType(ConnectionPoolType.TOKEN_AWARE))
				.withConnectionPoolConfiguration(
						new ConnectionPoolConfigurationImpl(TEST_CLUSTER_NAME
								+ "_" + TEST_KEYSPACE_NAME)
						.setSocketTimeout(30000)
						.setMaxTimeoutWhenExhausted(2000)
						.setMaxConnsPerHost(20)
						.setInitConnsPerHost(10)
						.setSeeds(SEEDS))
						.withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
						.buildKeyspace(ThriftFamilyFactory.getInstance());

		keyspaceContext.start();

		keyspace = keyspaceContext.getEntity();

		try {
			keyspace.dropKeyspace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		keyspace.createKeyspace(ImmutableMap.<String, Object>builder()
				.put("strategy_options", ImmutableMap.<String, Object>builder()
						.put("replication_factor", "1")
						.build())
						.put("strategy_class",     "SimpleStrategy")
						.build()
				);

//		keyspace.createColumnFamily(CF_SAMPLE_ENTITY, null);
//		keyspace.createColumnFamily(CF_SIMPLE_ENTITY, null);
		{
		    EntityManager<SampleEntity, String> entityPersister = new DefaultEntityManager.Builder<SampleEntity, String>()
                        .withEntityType(SampleEntity.class)
                        .withKeyspace(keyspace)
                        .build();
            entityPersister.createStorage(null);
        }

        {
            EntityManager<SimpleEntity, String> entityPersister = new DefaultEntityManager.Builder<SimpleEntity, String>()
                        .withEntityType(SimpleEntity.class)
                        .withKeyspace(keyspace)
                        .build();
            entityPersister.createStorage(null);
        }

	}

	private SampleEntity createSampleEntity(String id) {
		Random prng = new Random();
		SampleEntity entity = new SampleEntity();
		entity.setId(id);
		entity.setBooleanPrimitive(prng.nextBoolean());
		entity.setBooleanObject(prng.nextBoolean());
		entity.setBytePrimitive((byte)prng.nextInt(Byte.MAX_VALUE));
		entity.setByteObject((byte)prng.nextInt(Byte.MAX_VALUE));
		entity.setShortPrimitive((short)prng.nextInt(Short.MAX_VALUE));
		entity.setShortObject((short)prng.nextInt(Short.MAX_VALUE));
		entity.setIntPrimitive(prng.nextInt());
		entity.setIntObject(prng.nextInt());
		entity.setLongPrimitive(prng.nextLong());
		entity.setLongObject(prng.nextLong());
		entity.setFloatPrimitive(prng.nextFloat());
		entity.setFloatObject(prng.nextFloat());
		entity.setDoublePrimitive(prng.nextDouble());
		entity.setDoubleObject(prng.nextDouble());
		entity.setString(RandomStringUtils.randomAlphanumeric(16));
		entity.setByteArray(RandomStringUtils.randomAlphanumeric(16).getBytes(Charsets.UTF_8));
		entity.setDate(new Date());
		entity.setUuid(TimeUUIDUtils.getUniqueTimeUUIDinMicros());
		Foo foo = new Foo(prng.nextInt(), RandomStringUtils.randomAlphanumeric(4));
		entity.setFoo(foo);
		BarBar barbar = new BarBar();
		barbar.i = prng.nextInt();
		barbar.s = RandomStringUtils.randomAlphanumeric(4);
		Bar bar = new Bar();
		bar.i = prng.nextInt();
		bar.s = RandomStringUtils.randomAlphanumeric(4);
		bar.barbar = barbar;
		entity.setBar(bar);
		return entity;
	}

	@Test
	public void basicLifecycle() throws Exception {
		final String id = "basicLifecycle";
		EntityManager<SampleEntity, String> entityPersister = new DefaultEntityManager.Builder<SampleEntity, String>()
				.withEntityType(SampleEntity.class)
				.withKeyspace(keyspace)
				.build();
		SampleEntity origEntity = createSampleEntity(id);

		entityPersister.put(origEntity);

		// use low-level astyanax API to confirm the write
		{
			ColumnList<String> cl = keyspace.prepareQuery(CF_SAMPLE_ENTITY).getKey(id).execute().getResult();

			// 19 simple columns
			// 2 one-level-deep nested columns from Bar
			// 2 two-level-deep nested columns from BarBar
			Assert.assertEquals(23, cl.size());

			// simple columns
			Assert.assertEquals(origEntity.getString(), cl.getColumnByName("STRING").getStringValue());
			Assert.assertArrayEquals(origEntity.getByteArray(), cl.getColumnByName("BYTE_ARRAY").getByteArrayValue());

			//  nested fields
			Assert.assertEquals(origEntity.getBar().i, cl.getColumnByName("BAR.i").getIntegerValue());
			Assert.assertEquals(origEntity.getBar().s, cl.getColumnByName("BAR.s").getStringValue());
			Assert.assertEquals(origEntity.getBar().barbar.i, cl.getColumnByName("BAR.barbar.i").getIntegerValue());
			Assert.assertEquals(origEntity.getBar().barbar.s, cl.getColumnByName("BAR.barbar.s").getStringValue());
		}

		SampleEntity getEntity = entityPersister.get(id);
		Assert.assertEquals(origEntity, getEntity);

		entityPersister.delete(id);

		// use low-level astyanax API to confirm the delete
		{
			ColumnList<String> cl = keyspace.prepareQuery(CF_SAMPLE_ENTITY).getKey(id).execute().getResult();
			Assert.assertEquals(0, cl.size());
		}
	}

	@Test
	public void testMultiCalls() throws Exception {
		EntityManager<SimpleEntity, String> entityPersister = new DefaultEntityManager.Builder<SimpleEntity, String>()
				.withEntityType(SimpleEntity.class)
				.withKeyspace(keyspace)
				.build();
        
		final Map<String, SimpleEntity> entities = Maps.newHashMap();
		for (int i = 0; i < 10; i++) {
			String str = Integer.toString(i);
			entities.put(str, new SimpleEntity(str, str));
		}

		// Add multiple
		entityPersister.put(entities.values());

		{
			final Map<String, SimpleEntity> entities2 = collectionToMap(entityPersister.get(entities.keySet()));

			Assert.assertEquals(entities.keySet(), entities2.keySet());
		}

		// Read all
		{
			final Map<String, SimpleEntity> entities2 = collectionToMap(entityPersister.getAll());

			Assert.assertEquals(entities.keySet(), entities2.keySet());
		}

		// Delete multiple
		{
			System.out.println(entities.keySet());
			entityPersister.delete(entities.keySet());

			final Map<String, SimpleEntity> entities3 = collectionToMap(entityPersister.get(entities.keySet()));

			System.out.println(entities3);
			Assert.assertTrue(entities3.isEmpty());

			final Map<String, SimpleEntity> entities4 = collectionToMap(entityPersister.getAll());

			System.out.println(entities4);
			Assert.assertTrue(entities4.isEmpty());
		}        
	}

	private static Map<String, SimpleEntity> collectionToMap(Collection<SimpleEntity> entities) {
		Map<String, SimpleEntity> map = Maps.newHashMap();
		for (SimpleEntity entity : entities) {
			map.put(entity.getId(),  entity);
		}
		return map;
	}

	private DoubleIdColumnEntity createDoubleIdColumnEntity(String id) {
		Random prng = new Random();
		DoubleIdColumnEntity entity = new DoubleIdColumnEntity();
		entity.setId(id);
		entity.setNum(prng.nextInt());
		entity.setStr(RandomStringUtils.randomAlphanumeric(4));
		return entity;
	}

	@Test
	public void doubleIdColumnAnnotation() throws Exception {
		final String id = "doubleIdColumnAnnotation";
		EntityManager<DoubleIdColumnEntity, String> entityPersister = new DefaultEntityManager.Builder<DoubleIdColumnEntity, String>()
				.withEntityType(DoubleIdColumnEntity.class)
				.withKeyspace(keyspace)
				.withColumnFamily(CF_SAMPLE_ENTITY)
				.build();
		DoubleIdColumnEntity origEntity = createDoubleIdColumnEntity(id);

		entityPersister.put(origEntity);

		// use low-level astyanax API to confirm the write
		{
			ColumnList<String> cl = keyspace.prepareQuery(CF_SAMPLE_ENTITY).getKey(id).execute().getResult();
			// test column number
			Assert.assertEquals(3, cl.size());
			// test column value
			Assert.assertEquals(origEntity.getId(), cl.getColumnByName("id").getStringValue());
			Assert.assertEquals(origEntity.getNum(), cl.getColumnByName("num").getIntegerValue());
			Assert.assertEquals(origEntity.getStr(), cl.getColumnByName("str").getStringValue());
		}

		DoubleIdColumnEntity getEntity = entityPersister.get(id);
		Assert.assertEquals(origEntity, getEntity);

		entityPersister.delete(id);

		// use low-level astyanax API to confirm the delete
		{
			ColumnList<String> cl = keyspace.prepareQuery(CF_SAMPLE_ENTITY).getKey(id).execute().getResult();
			Assert.assertEquals(0, cl.size());
		}
	}
	
	private TtlEntity createTtlEntity(String id) {
		TtlEntity e = new TtlEntity();
		e.setId(id);
		e.setColumn(RandomStringUtils.randomAlphanumeric(4));
		return e;
	}
	
	@Test
	public void testTtlAnnotation() throws Exception {
		final String id = "testTtlAnnotation";
		EntityManager<TtlEntity, String> entityPersister = new DefaultEntityManager.Builder<TtlEntity, String>()
				.withEntityType(TtlEntity.class)
				.withKeyspace(keyspace)
				.withColumnFamily(CF_SAMPLE_ENTITY)
				.build();
		TtlEntity origEntity = createTtlEntity(id);

		entityPersister.put(origEntity);

		// use low-level astyanax API to confirm the write
		{
			ColumnList<String> cl = keyspace.prepareQuery(CF_SAMPLE_ENTITY).getKey(id).execute().getResult();
			// test column number
			Assert.assertEquals(1, cl.size());
			// test column value
			Assert.assertEquals(origEntity.getColumn(), cl.getColumnByName("column").getStringValue());
			// custom ttl
			Assert.assertEquals(2, cl.getColumnByName("column").getTtl());
		}

		TtlEntity getEntity = entityPersister.get(id);
		Assert.assertEquals(origEntity, getEntity);

		// entity should expire after 3s since TTL is 2s in annotation
		Thread.sleep(1000 * 3);

		// use low-level astyanax API to confirm the TTL expiration
		{
			ColumnList<String> cl = keyspace.prepareQuery(CF_SAMPLE_ENTITY).getKey(id).execute().getResult();
			Assert.assertEquals(0, cl.size());
		}
	}
	
	@Test
	public void testTtlOverride() throws Exception {
		final String id = "testTtlAnnotation";
		EntityManager<TtlEntity, String> entityPersister = new DefaultEntityManager.Builder<TtlEntity, String>()
				.withEntityType(TtlEntity.class)
				.withKeyspace(keyspace)
				.withColumnFamily(CF_SAMPLE_ENTITY)
				.withTTL(5)
				.build();
		TtlEntity origEntity = createTtlEntity(id);

		entityPersister.put(origEntity);

		// use low-level astyanax API to confirm the write
		{
			ColumnList<String> cl = keyspace.prepareQuery(CF_SAMPLE_ENTITY).getKey(id).execute().getResult();
			// test column number
			Assert.assertEquals(1, cl.size());
			// test column value
			Assert.assertEquals(origEntity.getColumn(), cl.getColumnByName("column").getStringValue());
			// custom ttl
			Assert.assertEquals(5, cl.getColumnByName("column").getTtl());
		}

		TtlEntity getEntity = entityPersister.get(id);
		Assert.assertEquals(origEntity, getEntity);

		// entity should still be alive after 3s since TTL is overriden to 5s
		Thread.sleep(1000 * 3);
		
		getEntity = entityPersister.get(id);
		Assert.assertEquals(origEntity, getEntity);
		
		// entity should expire after 3s since 6s have passed with 5s TTL
		Thread.sleep(1000 * 3);

		// use low-level astyanax API to confirm the TTL expiration
		{
			ColumnList<String> cl = keyspace.prepareQuery(CF_SAMPLE_ENTITY).getKey(id).execute().getResult();
			Assert.assertEquals(0, cl.size());
		}
	}

}
