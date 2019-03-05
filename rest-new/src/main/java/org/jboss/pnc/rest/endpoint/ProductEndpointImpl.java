package org.jboss.pnc.rest.endpoint;

import org.jboss.pnc.dto.BuildConfiguration;
import org.jboss.pnc.dto.Product;
import org.jboss.pnc.dto.ProductVersion;
import org.jboss.pnc.dto.response.Page;
import org.jboss.pnc.facade.providers.ProductProvider;
import org.jboss.pnc.rest.api.endpoints.ProductEndpoint;
import org.jboss.pnc.rest.api.parameters.PageParameters;

import javax.inject.Inject;

public class ProductEndpointImpl implements ProductEndpoint {

    @Inject
    private ProductProvider productProvider;

    @Override
    public Page<Product> getAll(PageParameters pageParameters) {
        return productProvider.getAll(pageParameters.getPageIndex(),
                                      pageParameters.getPageSize(),
                                      pageParameters.getSort(),
                                      pageParameters.getQ());
    }

    @Override
    public Product createNew(Product product) {
        return null;
    }

    @Override
    public Product getSpecific(int id) {
        return null;
    }

    @Override
    public void update(int id, Product product) {

    }

    @Override
    public Page<BuildConfiguration> getBuildConfigurations(int id, PageParameters pageParams) {
        return null;
    }

    @Override
    public Page<ProductVersion> getProductVersions(int id, PageParameters pageParameters) {
        return null;
    }
}
