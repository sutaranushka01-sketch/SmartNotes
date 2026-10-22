package com.studenthub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.studenthub.service.PosHelper;                              // add
import org.springframework.boot.context.event.ApplicationReadyEvent;  // add
import org.springframework.context.event.EventListener;               // add


@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
	 @EventListener(ApplicationReadyEvent.class)                       // add
    public void warmUpPos() {                                         // add
        PosHelper.tag("The database stores data in tables.");         // add
    }          

}
