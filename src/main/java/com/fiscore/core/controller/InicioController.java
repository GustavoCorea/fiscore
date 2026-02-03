package com.fiscore.core.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;



@SessionScope
@Controller
public class InicioController {

	private final LoginController loginController;

    public InicioController(LoginController loginController) {
        this.loginController = loginController;
    }

    @GetMapping("/inicio")
	public String inicio(HttpSession session, Model model) {
		loginController.initAuthentication(session, model);
		return "index";
	}


}
