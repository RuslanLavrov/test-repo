package org.folio.unit.snapshot;

import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.folio.dao.snapshot.ProfileSnapshotDao;
import org.folio.dao.snapshot.ProfileSnapshotDaoImpl;
import org.folio.dao.snapshot.ProfileSnapshotItem;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ChildSnapshotWrapper;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.snapshot.ProfileSnapshotService;
import org.folio.services.snapshot.ProfileSnapshotServiceImpl;
import org.folio.unit.AbstractUnitTest;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.MATCH_PROFILE;

public class ProfileSnapshotServiceTest extends AbstractUnitTest {
  private static final String TABLE_NAME = "profile_snapshots";

  @Autowired
  private ProfileSnapshotDao dao;
  @Autowired
  private ProfileSnapshotService service;

  @Test
  public void shouldSaveAndReturnWrappersOnGetById(TestContext context) {
    Async async = context.async();

    ProfileSnapshotWrapper expectedJobProfileWrapper = new ProfileSnapshotWrapper()
      .withId(UUID.randomUUID().toString())
      .withContentType(JOB_PROFILE)
      .withContent(new JobProfile())
      .withChildSnapshotWrappers(Collections.singletonList(
        new ChildSnapshotWrapper()
          .withId(UUID.randomUUID().toString())
          .withContentType(ProfileSnapshotWrapper.ContentType.MATCH_PROFILE)
          .withContent(new MatchProfile())
          .withChildSnapshotWrappers(Collections.singletonList(
            new ChildSnapshotWrapper()
              .withId(UUID.randomUUID().toString())
              .withContentType(ProfileSnapshotWrapper.ContentType.ACTION_PROFILE)
              .withContent(new ActionProfile())
              .withChildSnapshotWrappers(Collections.singletonList(
                new ChildSnapshotWrapper()
                  .withId(UUID.randomUUID().toString())
                  .withContentType(ProfileSnapshotWrapper.ContentType.MAPPING_PROFILE)
                  .withContent(new MappingProfile())
              ))
          ))
      ));

    dao.save(expectedJobProfileWrapper, TENANT_ID).compose(ar -> {
      service.getById(expectedJobProfileWrapper.getId(), TENANT_ID).compose(optionalAr -> {
        context.assertTrue(optionalAr.isPresent());

        ProfileSnapshotWrapper actualJobProfileWrapper = optionalAr.get();
        context.assertEquals(expectedJobProfileWrapper.getId(), actualJobProfileWrapper.getId());
        context.assertEquals(expectedJobProfileWrapper.getContentType(), actualJobProfileWrapper.getContentType());
        context.assertEquals(expectedJobProfileWrapper.getContent().getClass(), actualJobProfileWrapper.getContent().getClass());

        ChildSnapshotWrapper expectedMatchProfileWrapper = expectedJobProfileWrapper.getChildSnapshotWrappers().get(0);
        ChildSnapshotWrapper actualMatchProfileWrapper = actualJobProfileWrapper.getChildSnapshotWrappers().get(0);
        assertExpectedChildOnActualChild(expectedMatchProfileWrapper, actualMatchProfileWrapper, context);

        ChildSnapshotWrapper expectedActionProfileWrapper = expectedMatchProfileWrapper.getChildSnapshotWrappers().get(0);
        ChildSnapshotWrapper actualActionProfileWrapper = actualMatchProfileWrapper.getChildSnapshotWrappers().get(0);
        assertExpectedChildOnActualChild(expectedActionProfileWrapper, actualActionProfileWrapper, context);

        ChildSnapshotWrapper expectedMappingProfileWrapper = expectedActionProfileWrapper.getChildSnapshotWrappers().get(0);
        ChildSnapshotWrapper actualMappingProfileWrapper = actualActionProfileWrapper.getChildSnapshotWrappers().get(0);
        assertExpectedChildOnActualChild(expectedMappingProfileWrapper, actualMappingProfileWrapper, context);

        async.complete();
        return Future.succeededFuture();
      });
      return Future.succeededFuture();
    });
  }

  @Test
  public void shouldReturnFailedFutureIfNoSnapshotItemsExist(TestContext context) {
    Async async = context.async();
    // given
    ProfileSnapshotDao mockDao = Mockito.mock(ProfileSnapshotDaoImpl.class);
    ProfileSnapshotService service = new ProfileSnapshotServiceImpl(dao);

    String jobProfileId = UUID.randomUUID().toString();
    Mockito.when(mockDao.getSnapshotItems(jobProfileId, TENANT_ID)).thenReturn(Future.succeededFuture(new ArrayList<>()));

    // when
    service.createSnapshot(jobProfileId, TENANT_ID).setHandler(ar -> {
      // then
      context.assertTrue(ar.failed());
      async.complete();
    });
  }

  @Test
  public void shouldBuildAndSaveSnapshotForJobProfile(TestContext testContext) {
    Async async = testContext.async();
    // given
    ProfileSnapshotDao mockDao = Mockito.mock(ProfileSnapshotDaoImpl.class);
    ProfileSnapshotService service = new ProfileSnapshotServiceImpl(mockDao);

    JobProfile jobProfile = new JobProfile().withId(UUID.randomUUID().toString());
    ProfileSnapshotItem jobProfileSnapshotItem = new ProfileSnapshotItem();
    jobProfileSnapshotItem.setAssociationId(UUID.randomUUID().toString());
    jobProfileSnapshotItem.setMasterId(null);
    jobProfileSnapshotItem.setDetailId(jobProfile.getId());
    jobProfileSnapshotItem.setDetailType(JOB_PROFILE);
    jobProfileSnapshotItem.setDetail(jobProfile);

    MatchProfile matchProfile = new MatchProfile().withId(UUID.randomUUID().toString());
    ProfileSnapshotItem matchProfileSnapshotItem = new ProfileSnapshotItem();
    matchProfileSnapshotItem.setAssociationId(UUID.randomUUID().toString());
    matchProfileSnapshotItem.setMasterId(jobProfile.getId());
    matchProfileSnapshotItem.setDetailId(matchProfile.getId());
    matchProfileSnapshotItem.setDetailType(MATCH_PROFILE);
    matchProfileSnapshotItem.setDetail(matchProfile);

    ActionProfile actionProfile = new ActionProfile().withId(UUID.randomUUID().toString());
    ProfileSnapshotItem actionProfileSnapshotItem = new ProfileSnapshotItem();
    actionProfileSnapshotItem.setAssociationId(UUID.randomUUID().toString());
    actionProfileSnapshotItem.setMasterId(matchProfile.getId());
    actionProfileSnapshotItem.setDetailId(actionProfile.getId());
    actionProfileSnapshotItem.setDetailType(ACTION_PROFILE);
    actionProfileSnapshotItem.setDetail(actionProfile);

    MappingProfile mappingProfile = new MappingProfile().withId(UUID.randomUUID().toString());
    ProfileSnapshotItem mappingProfileSnapshotItem = new ProfileSnapshotItem();
    mappingProfileSnapshotItem.setAssociationId(UUID.randomUUID().toString());
    mappingProfileSnapshotItem.setMasterId(actionProfile.getId());
    mappingProfileSnapshotItem.setDetailId(mappingProfile.getId());
    mappingProfileSnapshotItem.setDetailType(MAPPING_PROFILE);
    mappingProfileSnapshotItem.setDetail(mappingProfile);

    List<ProfileSnapshotItem> items = new ArrayList<>(Arrays.asList(
      jobProfileSnapshotItem,
      actionProfileSnapshotItem,
      matchProfileSnapshotItem,
      mappingProfileSnapshotItem)
    );

    Mockito.when(mockDao.getSnapshotItems(jobProfile.getId(), TENANT_ID)).thenReturn(Future.succeededFuture(items));
    Mockito.when(mockDao.save(ArgumentMatchers.any(), ArgumentMatchers.anyString())).thenReturn(Future.succeededFuture(jobProfile.getId()));

    // when
    service.createSnapshot(jobProfile.getId(), TENANT_ID).setHandler(ar -> {
      // then
      testContext.assertTrue(ar.succeeded());
      ProfileSnapshotWrapper jobProfileWrapper = ar.result();
      JobProfile actualJobProfile = (JobProfile) jobProfileWrapper.getContent();
      testContext.assertEquals(jobProfile.getId(), actualJobProfile.getId());

      ChildSnapshotWrapper matchProfileWrapper = jobProfileWrapper.getChildSnapshotWrappers().get(0);
      MatchProfile actualMatchProfile = (MatchProfile) matchProfileWrapper.getContent();
      testContext.assertEquals(matchProfile.getId(), actualMatchProfile.getId());

      ChildSnapshotWrapper actionProfileWrapper = matchProfileWrapper.getChildSnapshotWrappers().get(0);
      ActionProfile actualActionProfile = (ActionProfile) actionProfileWrapper.getContent();
      testContext.assertEquals(actionProfile.getId(), actualActionProfile.getId());

      ChildSnapshotWrapper mappingProfileWrapper = actionProfileWrapper.getChildSnapshotWrappers().get(0);
      MappingProfile actualMappingProfile = (MappingProfile) mappingProfileWrapper.getContent();
      testContext.assertEquals(mappingProfile.getId(), actualMappingProfile.getId());
      async.complete();
    });
  }

  private void assertExpectedChildOnActualChild(ChildSnapshotWrapper expected, ChildSnapshotWrapper actual, TestContext context) {
    context.assertEquals(expected.getId(), actual.getId());
    context.assertEquals(expected.getContentType(), actual.getContentType());
    context.assertEquals(expected.getContent().getClass(), actual.getContent().getClass());
  }

  @After
  public void afterTest(TestContext context) {
    Async async = context.async();
    PostgresClient.getInstance(vertx, TENANT_ID).delete(TABLE_NAME, new Criterion(), event -> {
      if (event.failed()) {
        context.fail(event.cause());
      }
      async.complete();
    });
  }
}