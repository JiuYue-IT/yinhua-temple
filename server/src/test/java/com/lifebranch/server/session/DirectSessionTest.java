package com.lifebranch.server.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifebranch.server.config.AppProperties;
import com.lifebranch.server.device.DryRunDeviceBridge;
import com.lifebranch.server.error.ApiException;
import com.lifebranch.server.model.*;
import com.lifebranch.server.story.*;
import com.lifebranch.server.story.ai.ReadingPrompt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class DirectSessionTest {
    private SessionService service;
    private final DryRunDeviceBridge device = new DryRunDeviceBridge();
    @AfterEach void close() { if (service != null) service.shutdown(); }

    private SessionService createService(ReadingProvider provider, int timeout) throws Exception {
        var props = new AppProperties(new AppProperties.Ai(AppProperties.AiProvider.OPENAI, "http://test", "key", "m",
                "bearer", "low", true, false, 4000, 5, timeout),
                new AppProperties.Device(DeviceMode.DRYRUN, null), new AppProperties.Preset(0));
        var validator = new StoryValidator();
        var catalog = new PresetCatalog(new ObjectMapper(), validator);
        service = new SessionService(props, catalog, new PresetStoryProvider(props),
                input -> { throw new AssertionError("direct must not generate an exploration story"); },
                validator, device, provider, new ReadingPrompt());
        return service;
    }
    private CreateSessionRequest live(String concern) {
        return new CreateSessionRequest(UUID.randomUUID().toString(), SessionMode.LIVE, null, null,
                ExperienceMode.DIRECT, new WishInput(concern, null, null, null, null));
    }
    private SessionSnapshot await(String id, SessionStatus expected) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        do {
            var snap=service.get(id);
            if(snap.status()==expected)return snap;
            Thread.sleep(10);
        } while(System.nanoTime()<end);
        throw new AssertionError("Expected "+expected+", got "+service.get(id).status());
    }
    @Test void earlySingleGenerationThenSignWithoutChoiceAndDetailedReceipt() throws Exception {
        var calls=new AtomicInteger();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var reading=new ReadingPrompt().preset();
        createService(input->{calls.incrementAndGet();entered.countDown();release.await();return reading;},30);
        var request=live("  希望把项目做好  ");var snap=service.create(request);
        assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
        assertThat(snap.status()).isEqualTo(SessionStatus.GENERATING);
        assertThat(snap.wish().concern()).isEqualTo("希望把项目做好");
        assertThat(snap.input()).isNull();assertThat(snap.story()).isNull();
        assertThat(service.create(request).id()).isEqualTo(snap.id());
        assertThat(device.history()).isEmpty();
        assertThatThrownBy(()->service.drawSign(snap.id())).isInstanceOf(ApiException.class);
        release.countDown();var ready=await(snap.id(),SessionStatus.READY);
        assertThat(ready.reading().summary()).isEqualTo(reading.summary());
        assertThat(ready.reading().detail()).isEqualTo(reading.detail());
        assertThatThrownBy(()->service.choose(snap.id(),"A")).isInstanceOf(ApiException.class);
        assertThat(service.drawSign(snap.id()).status()).isEqualTo(SessionStatus.SIGN_DRAWING);
        service.drawSign(snap.id());var drawn=await(snap.id(),SessionStatus.SIGN_READY);
        assertThat(drawn.sign().title()).isEqualTo(reading.summary().title());
        var receipt=new ReceiptRequest(reading.summary().message(),reading.detail().nextStep());
        var completed=service.confirmReceipt(snap.id(),receipt);service.confirmReceipt(snap.id(),receipt);
        assertThat(completed.receipt().concern()).isEqualTo("希望把项目做好");
        assertThat(completed.receipt().chosenPath()).isNull();
        assertThat(completed.receipt().experience()).isEqualTo(ExperienceMode.DIRECT);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(device.history()).containsExactly(DeviceEvent.SIGN,DeviceEvent.SIGN_RESULT,DeviceEvent.RECEIPT);
    }
    @Test void directPresetReturnsBothPartsWithoutLiveModel() throws Exception {
        createService(input->{throw new AssertionError("preset must not call AI");},30);
        var req=new CreateSessionRequest(UUID.randomUUID().toString(),SessionMode.PRESET,"team-project",null,ExperienceMode.DIRECT,null);
        var snap=service.create(req);snap=await(snap.id(),SessionStatus.READY);
        assertThat(snap.mode()).isEqualTo(SessionMode.PRESET);
        assertThat(snap.reading().detail().basis()).contains("预置案例");
    }
    @Test void validatesStructuredInputAndRejectsMixedContracts() throws Exception {
        var reading=new ReadingPrompt().preset();
        createService(input->reading,30);
        assertThatThrownBy(()->service.create(live(" "))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.create(live("字".repeat(401)))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->new WishInput("心事","字".repeat(401),null,null,null).normalized()).isInstanceOf(ApiException.class);
        var request=live("心事");
        var mixed=new CreateSessionRequest(request.requestId(),SessionMode.LIVE,null,new Input("背景","已选","未选","目标"),ExperienceMode.DIRECT,request.wish());
        assertThatThrownBy(()->service.create(mixed)).isInstanceOf(ApiException.class);
        assertThat(device.history()).isEmpty();
    }
    @Test void malformedReadingFailsWithoutShowingInventedContent() throws Exception {
        createService(input->new Reading(null,null),30);
        var snap=service.create(live("心事"));snap=await(snap.id(),SessionStatus.ERROR);
        assertThat(snap.error().code()).isEqualTo("AI_FORMAT_ERROR");assertThat(snap.reading()).isNull();
    }
    @Test void requestIdIncludesExperienceAndOptionalContextInFingerprint() throws Exception {
        var reading=new ReadingPrompt().preset();createService(input->reading,30);
        String id=UUID.randomUUID().toString();
        var wish=new WishInput(" 心事 "," 背景 ",null,null," 目标 ");
        var first=new CreateSessionRequest(id,SessionMode.LIVE,null,null,ExperienceMode.DIRECT,wish);
        var created=service.create(first);
        assertThat(service.create(new CreateSessionRequest(id,SessionMode.LIVE,null,null,ExperienceMode.DIRECT,wish.normalized())).id()).isEqualTo(created.id());
        assertThatThrownBy(()->service.create(new CreateSessionRequest(id,SessionMode.LIVE,null,null,ExperienceMode.DIRECT,
                new WishInput("心事","背景",null,null,"别的目标")))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.create(new CreateSessionRequest(id,SessionMode.LIVE,null,
                new Input("背景","已选","未选","目标"),ExperienceMode.EXPLORE,null))).isInstanceOf(ApiException.class);
    }
    @Test void resetDropsLateReadingFromPreviousInput() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var reading=new ReadingPrompt().preset();
        createService(input->{entered.countDown();while(!release.await(20,TimeUnit.MILLISECONDS)){}return reading;},30);
        var old=service.create(live("旧心事"));assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
        service.reset();release.countDown();Thread.sleep(80);
        assertThat(service.currentSnapshot()).isNull();
        assertThatThrownBy(()->service.get(old.id())).isInstanceOf(ApiException.class);
    }
    @Test void directGenerationHasTotalTimeout() throws Exception {
        var reading=new ReadingPrompt().preset();
        createService(input->{Thread.sleep(5000);return reading;},1);
        var snap=service.create(live("心事"));snap=await(snap.id(),SessionStatus.ERROR);
        assertThat(snap.error().code()).isEqualTo("AI_TIMEOUT");assertThat(snap.reading()).isNull();
    }
}
