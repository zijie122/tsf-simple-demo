package com.tsf.demo.consumer.proxy;

import com.tencent.tsf.unit.annotation.TsfUnitRule;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient(name = "provider-demo")
public interface ProviderDemoService {
    @RequestMapping(value = "/echo/{str}", method = RequestMethod.GET)
    String echo(@PathVariable("str") String str);

    @RequestMapping(value = "/echoTracer/{str}", method = RequestMethod.GET)
    String echoTracer(@PathVariable("str") String str);

    @RequestMapping(value = "/echo/error/{str}", method = RequestMethod.GET)
    String echoError(@PathVariable("str") String str);

    @RequestMapping(value = "/echo/slow/{str}", method = RequestMethod.GET)
    String echoSlow(@PathVariable("str") String str);

    @TsfUnitRule(ruleGenerator = "com.tsf.demo.consumer.util.UserIdGenerator")
    @RequestMapping(value = "/echo/unit/{str}", method = RequestMethod.GET)
    String echoUnit(@PathVariable("str") String str);
}