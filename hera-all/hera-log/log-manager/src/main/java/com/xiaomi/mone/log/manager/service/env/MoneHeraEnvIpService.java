/*
 * Copyright 2020 Xiaomi
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.xiaomi.mone.log.manager.service.env;

import com.google.common.collect.Lists;
import com.xiaomi.mone.log.api.model.meta.LogPattern;
import com.xiaomi.mone.log.manager.model.vo.LogAgentListBo;
import com.xiaomi.youpin.docean.anno.Service;

import java.util.List;
import java.util.Map;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2022/11/16 15:41
 */
@Service
public class MoneHeraEnvIpService implements HeraEnvIpService {

    @Override
    public List<LogAgentListBo> queryInfoByNodeIp(String nodeIp) {
        return Lists.newArrayList();
    }

    @Override
    public Map<String, List<LogAgentListBo>> queryAgentIpByPodIps(List<String> podIps) {
        return null;
    }

    @Override
    public List<LogPattern.IPRel> queryActualIps(List<String> ips, String agentIp) {
        return Lists.newArrayList(LogPattern.IPRel.builder().ip(agentIp).build());
    }
}
