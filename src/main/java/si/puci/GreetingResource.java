package si.puci;

import java.time.OffsetDateTime;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;
import org.eclipse.microprofile.reactive.messaging.Channel;

import io.smallrye.context.api.ManagedExecutorConfig;
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
    @Channel("words-out")
    MutinyEmitter<String> emitter;

    @Inject
    @ManagedExecutorConfig(cleared = ThreadContext.TRANSACTION)
    ManagedExecutor noTxExecutor;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello()
    {
        final var myEntity = new MyEntity();
        myEntity.field = OffsetDateTime.now().toString();
        myEntity.persistAndFlush();

        emitter.sendAndAwait(myEntity.field);

        //this kinda solves the problem
        //noTxExecutor.runAsync(() -> emitter.sendAndAwait(myEntity.field)).join();

        return "Hello from RESTEasy Reactive";
    }
}
