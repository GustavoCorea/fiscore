package com.fiscore.core.controller;

import jakarta.servlet.ServletContext;
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
	@Autowired
	private ServletContext context;

	String nombreUsuario = null;

	@GetMapping("/inicio")
	public String inicio(Model model) {
		return "index";
	}


//	public InicioController(IDepartamentoService iDepartamentoService, IMunicipioService iMunicipioService,
//							IDistritoService iDistritoService, IUsuarioService iUsuarioService, PeriodoService periodoService, SolicitudService solicitudService, SolicitudDetalleService solicitudDetalleService) {
//		this.iDepartamentoService = iDepartamentoService;
//		this.iMunicipioService = iMunicipioService;
//		this.iDistritoService = iDistritoService;
//		this.iUsuarioService = iUsuarioService;
//		this.periodoService = periodoService;
//		this.solicitudService = solicitudService;
//		this.solicitudDetalleService = solicitudDetalleService;
//	}
	

//	@GetMapping("/inicio")
//	public String inicio(Model model) {
//		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//		if (authentication != null && authentication.isAuthenticated()) {
//			model.addAttribute("autenticado", authentication.getName());
//		}
//		return "index";
//	}
//
//	@GetMapping("/menuIngreso")
//	public String menuIngreso(Model model) {
//		return "menuIngreso";
//	}
//
//	@GetMapping("/menuLogin")
//	public String menuLogin(Model model) {
//		return "menuLogin";
//	}
//
//	@GetMapping(path = "/logoutSistema")
//	public String logoutSistema(HttpServletRequest request) throws ServletException {
//		request.logout();
//		return "redirect:"+util.rutaBaseRedirect(request.getServerName())+"menuIngreso";
//	}
//
//	@GetMapping("/login")
//	public String login(Model model,@RequestParam("idTipo") Integer idTipo) {
//		if(idTipo!=null) {
//
//			if(idTipo==1) {
//
//			model.addAttribute("tituloForm", "Inicio de Sesión Trabajador");
//			model.addAttribute("titulolabel", "Número de DUI");
//			model.addAttribute("trabajador", true);
//			model.addAttribute("empleador", false);
//			model.addAttribute("tipo", "T");
//
//			}
//			if(idTipo==2) {
//
//				model.addAttribute("tituloForm", "Inicio de Sesión Empleador");
//				model.addAttribute("titulolabel", "Número de DUI/NIT");
//				model.addAttribute("empleador", true);
//				model.addAttribute("trabajador", false);
//				model.addAttribute("tipo", "N");
//			 }
//		 }
//		else {
//			model.addAttribute("tituloForm", "Inicio de Sesión");
//			model.addAttribute("titulolabel", "Número de DUI");
//			model.addAttribute("trabajador", true);
//			model.addAttribute("empleador", false);
//			model.addAttribute("tipo", "T");
//		}
//		return "loginEmpleador";
//	}
//
//	@GetMapping("/loginEmpleador")
//	public String loginEmpleador(Model model) {
//		return "loginEmpleador";
//	}
//


}
