package com.example.urlshortener;

import org.springframework.boot.SpringApplication;

public class TestUrlshortenerApplication {

	public static void main(String[] args) {
		SpringApplication.from(UrlshortenerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
