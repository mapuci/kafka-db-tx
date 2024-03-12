package si.puci;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
}
