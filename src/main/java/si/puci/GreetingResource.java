package si.puci;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.commons.lang3.function.Failable;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestContext;

import io.quarkus.arc.Arc;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.Providers;

@Path("/hello")
@Transactional
@ApplicationScoped
public class GreetingResource
{
    @Inject
    @Channel("mutiny-emitter")
    MutinyEmitter<String> mutinyEmitter;
    @Inject
    @Channel("emitter")
    Emitter<String> emitter;
    @RestClient
    HelloClient helloClient;

    @GET
    @Path("/mutiny-emitter-send-and-await")
    @Produces(MediaType.TEXT_PLAIN)
    public String mutinyEmitterSendAndAwait()
    {
        /////////// THIS THROWS (1 in 1000) /////////////////

        final var myEntity = new MyEntity();
        myEntity.field = OffsetDateTime.now().toString();
        myEntity.persist();
        mutinyEmitter.sendAndAwait(myEntity.field);

        return "mutiny-emitter-send-and-await";
    }

    @GET
    @Path("/mutiny-emitter-send-and-forget")
    @Produces(MediaType.TEXT_PLAIN)
    public String mutinyEmitterSendAndForget()
    {
        /////////// THIS ALWAYS THROWS (expected) /////////////////
        final var myEntity = new MyEntity();
        myEntity.field = OffsetDateTime.now().toString();
        myEntity.persist();

        mutinyEmitter.sendAndForget(myEntity.field);

        //this cannot work, since propagated TX is still bound to kafka thread in time of commit
        return "mutiny-emitter-send-and-forget";
    }

    @GET
    @Path("/emitter-send-block-at-end")
    @Produces(MediaType.TEXT_PLAIN)
    public String emitterSendBlockAtEnd()
    {
        /////////// THIS THROWS (1 in 1000) /////////////////
        final var myEntity = new MyEntity();
        myEntity.field = OffsetDateTime.now().toString();
        myEntity.persist();

        final var completableFuture = new CompletableFuture<String>();
        emitter.send(
                Message.of(myEntity.field)
                        .withAck(() -> {
                            completableFuture.complete("emitter-send-block-at-end");
                            return CompletableFuture.completedFuture(null);
                        })
                        .withNack(t -> {
                            completableFuture.completeExceptionally(t);
                            return CompletableFuture.completedFuture(null);
                        }));
        return completableFuture.join();
    }
    @GET
    @Path("/emitter-send-return-fut")
    @Produces(MediaType.TEXT_PLAIN)
    public CompletionStage<String> emitterSend()
    {
        /////////// THIS DOES NOT THROW (no idea why)/////////////////
        final var myEntity = new MyEntity();
        myEntity.field = OffsetDateTime.now().toString();
        myEntity.persist();

        final var completableFuture = new CompletableFuture<String>();
        emitter.send(
                Message.of(myEntity.field)
                        .withAck(() -> {
                            completableFuture.complete("emitter-send-return-fut");
                            return CompletableFuture.completedFuture(null);
                        })
                        .withNack(t -> {
                            completableFuture.completeExceptionally(t);
                            return CompletableFuture.completedFuture(null);
                        }));
        return completableFuture;
    }

    @GET
    @Path("/rest-client-blocking")
    @Produces(MediaType.TEXT_PLAIN)
    public String restClientBlocking()
    {
        Log.infof("tx: %s", Failable.call(() -> Arc.container().select(TransactionManager.class).get().getStatus()));
        final var myEntity = new MyEntity();
        myEntity.field = OffsetDateTime.now().toString();
        myEntity.persist();
        helloClient.hello();

        return "rest-client-blocking";
    }

    @GET
    @Path("/save")
    @Produces(MediaType.TEXT_PLAIN)
    public String saveSomethingToDb()
    {
        final var myEntity = new MyEntitySequence();
        myEntity.field = OffsetDateTime.now().toString();
        myEntity.persist();

        return "saveSomethingToDb";
    }


    @GET
    @Path("/query-and-send")
    @Produces(MediaType.TEXT_PLAIN)
    public String queryAndSend()
    {
        final var panacheEntityBases = MyEntitySequence.listAll();
        mutinyEmitter.sendAndAwait("hello");
        return "sa";
    }

    @GET
    @Path("/world")
    @Produces(MediaType.TEXT_PLAIN)
    public String hello()
    {
        return "hello";
    }

    @RegisterRestClient(baseUri = "http://localhost:8081")
    @RegisterProvider(TestClientResponseFilter.class)
    interface HelloClient
    {
        @GET
        @Path("/hello/world")
        @Produces(MediaType.TEXT_PLAIN)
        String hello();
    }

    @Provider
    public static class TestClientResponseFilter implements ClientResponseFilter
    {

        @Override
        public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException
        {
            Log.infof("eventLoopTx: %s", Failable.call(() -> Arc.container().select(TransactionManager.class).get().getStatus()));
        }
    }
}
