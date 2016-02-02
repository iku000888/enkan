package kotowari.example;

import enkan.Application;
import enkan.application.WebApplication;
import enkan.config.ApplicationFactory;
import enkan.endpoint.ResourceEndpoint;
import enkan.middleware.*;
import enkan.predicate.NonePredicate;
import enkan.system.inject.ComponentInjector;
import kotowari.example.controller.ExampleController;
import kotowari.example.controller.LoginController;
import kotowari.middleware.*;
import kotowari.routing.Routes;

/**
 * @author kawasima
 */
public class MyApplicationFactory implements ApplicationFactory {
    @Override
    public Application create(ComponentInjector injector) {
        WebApplication app = new WebApplication();

        // Routing
        Routes routes = Routes.define(r -> {
            r.get("/").to(ExampleController.class, "index");
            r.get("/m1").to(ExampleController.class, "method1");
            r.get("/m2").to(ExampleController.class, "method2");
            r.get("/m3").to(ExampleController.class, "method3");
            r.get("/m4").to(ExampleController.class, "method4");
            r.post("/login").to(LoginController.class, "login");
        }).compile();

        // Enkan
        app.use(new DefaultCharsetMiddleware());
        app.use(new NonePredicate(), new ServiceUnavailableMiddleware<>(new ResourceEndpoint("/public/html/503.html")));
        app.use(new StacktraceMiddleware());
        app.use(new TraceMiddleware<>());
        app.use(new ContentTypeMiddleware());
        app.use(new HttpStatusCatMiddleware());
        app.use(new ParamsMiddleware());
        app.use(new NormalizationMiddleware());
        app.use(new CookiesMiddleware());
        app.use(new SessionMiddleware());
        // Kotowari
        app.use(new ResourceMiddleware());
        app.use(new RoutingMiddleware(routes));
        app.use(injector.inject(new DomaTransactionMiddleware<>()));
        app.use(new FormMiddleware());
        app.use(new ValidateFormMiddleware());
        app.use(new HtmlRenderer());
        app.use(new ControllerInvokerMiddleware(injector));

        return app;
    }
}
