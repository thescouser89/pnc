package org.jboss.pnc.facade.providers;

import org.jboss.pnc.dto.Product;
import org.jboss.pnc.dto.ProductRef;
import org.jboss.pnc.facade.mapper.api.ProductMapper;
import org.jboss.pnc.facade.rsql.mapper.ProductRSQLMapper;
import org.jboss.pnc.spi.datastore.repositories.ProductRepository;

import javax.ejb.Stateless;
import javax.inject.Inject;

@Stateless
public class ProductProvider extends AbstractProvider<org.jboss.pnc.model.Product, Product, ProductRef>{

    // For CDI / EJB default constructor
    public ProductProvider() {
    }

    @Inject
    public ProductProvider(ProductRepository productRepository, ProductMapper mapper, ProductRSQLMapper rsqlMapper) {
        super(productRepository, mapper, rsqlMapper);
    }
}