package com.petar.job_queue.handler_registry;

import com.petar.job_queue.handlers.JobHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HandlerRegistry {
    private final Map<String, JobHandler> handler_registry;

    @Autowired
    public HandlerRegistry(List<JobHandler> jobHandlers){
        Map<String, JobHandler> temp = new HashMap<>();
        for(JobHandler job: jobHandlers)
        {
            if(temp.containsKey(job.type()))
                throw new IllegalStateException("Job already exists: " + job.type());
            temp.put(job.type(), job);
        }
        handler_registry = temp;
    }

    public JobHandler get(String param)
    {
        return handler_registry.get(param);
    }


}
