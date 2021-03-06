package edu.stanford.bmir.protege.web.server.crud;

import edu.stanford.bmir.protege.web.server.crud.persistence.ProjectEntityCrudKitSettings;
import edu.stanford.bmir.protege.web.server.crud.persistence.ProjectEntityCrudKitSettingsRepository;
import edu.stanford.bmir.protege.web.shared.crud.EntityCrudKitPrefixSettings;
import edu.stanford.bmir.protege.web.shared.crud.EntityCrudKitSettings;
import edu.stanford.bmir.protege.web.shared.crud.EntityCrudKitSuffixSettings;
import edu.stanford.bmir.protege.web.shared.crud.uuid.UUIDSuffixSettings;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 21/08/2013
 */
public class ProjectEntityCrudKitHandlerCache {

    private final ProjectId projectId;

    private final ProjectEntityCrudKitSettingsRepository repository;

    private EntityCrudKitHandler<?, ?> cachedHandler;

    @Inject
    public ProjectEntityCrudKitHandlerCache(ProjectEntityCrudKitSettingsRepository repository, ProjectId projectId) {
        this.repository = checkNotNull(repository);
        this.projectId = checkNotNull(projectId);
    }

    /**
     * Gets the current {@link EntityCrudKitHandler}.
     * @return The current {@link EntityCrudKitHandler}.  Not {@code null}.
     */
    public synchronized EntityCrudKitHandler<?, ?> getHandler() {
        EntityCrudKitSettings<?> settings = getCurrentSettings();
        if (isCachedHandlerStale(settings)) {
            cachedHandler = EntityCrudKitRegistry.get().getHandler(settings);
        }
        return cachedHandler;
    }

    private EntityCrudKitSettings<?> getCurrentSettings() {
        ProjectEntityCrudKitSettings projectSettings = repository.findOne(projectId);
        if (projectSettings == null) {
            projectSettings = new ProjectEntityCrudKitSettings(projectId, getDefaultSettings());
            repository.save(projectSettings);
        }
        return projectSettings.getSettings();
    }

    private boolean isCachedHandlerStale(EntityCrudKitSettings<?> settings) {
        return cachedHandler == null || !settings.equals(cachedHandler.getSettings());
    }

    /**
     * Creates default settings, with default prefix and suffix settings.
     *
     * @return The default settings.  Not {@code null}.
     */
    private static EntityCrudKitSettings<?> getDefaultSettings() {
        return new EntityCrudKitSettings<EntityCrudKitSuffixSettings>(getDefaultPrefixSettings(), getDefaultSuffixSettings());
    }

    private static EntityCrudKitPrefixSettings getDefaultPrefixSettings() {
        return new EntityCrudKitPrefixSettings();
    }

    private static EntityCrudKitSuffixSettings getDefaultSuffixSettings() {
        return new UUIDSuffixSettings();
    }
}
