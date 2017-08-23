package enkan.component.doma2;

import enkan.component.ComponentLifecycle;
import enkan.component.DataSourceComponent;
import enkan.component.SystemComponent;
import enkan.exception.MisconfigurationException;
import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.ConfigProvider;
import org.seasar.doma.jdbc.SqlFileRepository;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;

import static enkan.util.ReflectionUtils.tryReflection;

/**
 * @author kawasima
 */
public class DomaProvider extends SystemComponent {
    private DataSource dataSource;
    private ConcurrentHashMap<String, Object> daoCache = new ConcurrentHashMap<>();

    /**
     * Gets the DAO.
     *
     * @param daoInterface a type of DAO
     * @param <T> a type of DAO
     * @return a DAO of the given class.
     */
    public <T> T getDao(Class<? extends T> daoInterface) {
        return (T) daoCache.computeIfAbsent(daoInterface.getName(), key ->
                tryReflection(() -> {
                    Class<? extends T> daoClass;
                    try {
                         daoClass = (Class<? extends T>) Class.forName(daoInterface.getName() + "Impl", true, daoInterface.getClassLoader());
                    } catch (ClassNotFoundException ex) {
                        throw new MisconfigurationException("doma2.DAO_IMPL_NOT_FOUND", daoInterface.getName(), ex);
                    }
                    Constructor<? extends T> daoConstructor = daoClass.getConstructor(DataSource.class);
                    return daoConstructor.newInstance(dataSource);
            }));
    }

    @Override
    protected ComponentLifecycle lifecycle() {
        return new ComponentLifecycle<DomaProvider>() {
            @Override
            public void start(DomaProvider component) {
                DataSourceComponent dataSourceComponent = getDependency(DataSourceComponent.class);
                component.dataSource = dataSourceComponent.getDataSource();
            }

            @Override
            public void stop(DomaProvider component) {
                component.dataSource = null;
                daoCache.values().stream()
                        .filter(ConfigProvider.class::isInstance)
                        .map(ConfigProvider.class::cast)
                        .map(ConfigProvider::getConfig)
                        .map(Config::getSqlFileRepository)
                        .forEach(SqlFileRepository::clearCache);
                daoCache.clear();
            }
        };
    }
}
