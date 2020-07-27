package com.icthh.xm.tmf.ms.activation.mapper;

import com.icthh.xm.tmf.ms.activation.model.v4.Service;
import com.icthh.xm.tmf.ms.activation.model.v4.ServiceCreate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ServiceMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "href", ignore = true)
    Service serviceCreateToService(ServiceCreate serviceCreate);
}
