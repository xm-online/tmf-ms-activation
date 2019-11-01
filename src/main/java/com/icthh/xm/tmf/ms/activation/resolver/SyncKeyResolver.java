package com.icthh.xm.tmf.ms.activation.resolver;

import com.icthh.xm.commons.lep.AppendLepKeyResolver;
import com.icthh.xm.lep.api.LepManagerService;
import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.lep.api.commons.SeparatorSegmentedLepKey;
import com.icthh.xm.tmf.ms.activation.model.Service;
import org.springframework.stereotype.Component;

@Component
public class SyncKeyResolver extends AppendLepKeyResolver {
    @Override
    protected String[] getAppendSegments(SeparatorSegmentedLepKey baseKey,
                                         LepMethod method, LepManagerService managerService) {
        Service service = getRequiredParam(method, "service", Service.class);
        String serviceType = service.getType();
        return new String[]{serviceType};
    }
}
