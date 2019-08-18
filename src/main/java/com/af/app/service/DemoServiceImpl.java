package com.af.app.service;


import com.af.framework.annotation.AfService;

@AfService
public class DemoServiceImpl implements DemoService {
    public String getName() {
        return "AF";
    }
}
