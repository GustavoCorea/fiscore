package com.fiscore.core.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.Serializable;
import java.util.List;


@SessionScope
@Controller
public class LoginController implements Serializable {

//	private final AuthenticationManager authenticationManager;
//	private final UsuarioService usuarioService;
//	private final TipoUsuarioService tipoService;
//	private final CambioContraseniaService cambioContraseniaService;
//	private final UtilidadesController util;
//
//	public LoginController(AuthenticationManager authenticationManager, UsuarioService usuarioService,
//						   TipoUsuarioService tipoService, CambioContraseniaService cambioContraseniaService, UtilidadesController util) {
//		this.authenticationManager = authenticationManager;
//		this.usuarioService = usuarioService;
//		this.cambioContraseniaService = cambioContraseniaService;
//		this.util = util;
//		this.tipoService = tipoService;
//	}
//
//	@PostMapping("/processLogin")
//    public String processLogin(@RequestParam("username") String username,
//    		@RequestParam("password") String password,
//    		@RequestParam("tipo") String tipo,
//    		Model model, RedirectAttributes flash,
//    		HttpServletRequest request) {
//      try {
//            // Autenticar al usuario
//            Authentication authentication = authenticationManager.authenticate(
//                    new UsernamePasswordAuthenticationToken(username, password));
//            // Establecer la autenticación en el contexto de seguridad
//            SecurityContextHolder.getContext().setAuthentication(authentication);
//
//            boolean encontrado=false;
//            Usuario usuario = new Usuario();
//
//            if(tipo.equals("T")) {
//            	 usuario = usuarioService.findByDUi(username);
//            }else {
//            	//busca primero por DUI
//            	usuario = usuarioService.findByDUi(username);
//            	if(usuario==null){
//            		 usuario = usuarioService.findByNit(username);
//            	}
//            }
//
//
//            System.out.println(usuario);
//
//
//            if(usuario==null) {
//            	if(tipo.equals("T")) {
//            		 flash.addFlashAttribute("error","Credenciales inválidas, por favor verificar.");
//            		 return "redirect:"+util.rutaBaseRedirect(request.getServerName())+"login?idTipo=2";
//            	}else {
//            		 flash.addFlashAttribute("error","Credenciales inválidas, por favor verificar.");
//            		 return "redirect:"+util.rutaBaseRedirect(request.getServerName())+"login?idTipo=1";
//            	}
//            }
//
//            //verifica que tenga del tipo seleccionado
//
//            List<TipoUsuario> tipoList = tipoService.findByUsetId(usuario.getUsetId());
//            for(TipoUsuario tipoUsuario : tipoList) {
//            	if(tipoUsuario.getTuseNombre().equals(tipo)) {
//            		encontrado=true;
//            	}
//            	else if(tipoUsuario.getTuseNombre().equals("J")) {
//            		encontrado=true;
//            	}
//            }
//
//            if(encontrado) {
//            	if(tipo.equals("T")) {
//
//            		 return "redirect:"+util.rutaBaseRedirect(request.getServerName())+"inicioTrabajador";
//            	}
//            	else {
//            		 //return "redirect:/inicioEmpleador";
//            		return "redirect:"+util.rutaBaseRedirect(request.getServerName())+"inicioEmpleador";
//            	}
//            }else {
//            	if(tipo.equals("T")) {
//            		flash.addFlashAttribute("error","Usted no tiene registros como empleado, por favor verificar.");
//            		 return "redirect:"+util.rutaBaseRedirect(request.getServerName())+"login?idTipo=2";
//            	}else {
//            		flash.addFlashAttribute("error","Usted no tiene registros como empleador, por favor verificar.");
//            		return "redirect:"+util.rutaBaseRedirect(request.getServerName())+"login?idTipo=1";
//            	}
//
//            }
//
//
//
//
//           /* else {
//            	String tipounico= tipo.get(0).getTuseNombre();
//            	if(tipounico=="N") {
//            		 return "redirect:/inicioEmpleador";
//            	}
//            	else {
//            		 return "redirect:/inicioTrabajador";
//            	}
//            }*/
//            //return "redirect:/inicioTrabajador";
//
//         }
//        catch (Exception e) {
//	        /*String mensaje = switch (e.getMessage()) {
//	            case "Bad credentials" -> "Credenciales inválidas, por favor verificar.";
//	            case "Invalid user" -> "El DUI ingresado no existe";
//	            default -> "Error al iniciar sesión, verifique su conexión";
//	        };*/
//	        // Manejar la excepción de autenticación fallida (por ejemplo, usuario/contraseña incorrectos)
//	       // model.addAttribute("error","Credenciales inválidas, por favor verificar.");
//	    	 flash.addFlashAttribute("error","Credenciales inválidas, por favor verificar.");
//	        //e.printStackTrace();
//	    	 return "redirect:"+util.rutaBaseRedirect(request.getServerName())+"login?idTipo=1";
//	    }
//
//    }
//
//	@PostMapping("/processLoginEmpleador")
//	public String processLoginEmpleador(@RequestParam("username") String username,
//			@RequestParam("password") String password, Model model) {
//		try {
//			// Autenticar al usuario
//			Authentication authentication = authenticationManager
//					.authenticate(new UsernamePasswordAuthenticationToken(username, password));
//
//			// Establecer la autenticación en el contexto de seguridad
//			SecurityContextHolder.getContext().setAuthentication(authentication);
//			// Redirigir al usuario a la página de inicio después de iniciar sesión
//			return "redirect:/planillasPendientes";
//		} catch (Exception e) {
//			/*
//			 * String mensaje = switch (e.getMessage()) { case "Bad credentials" ->
//			 * "Credenciales inválidas, por favor verificar."; case "Invalid user" ->
//			 * "El DUI ingresado no existe"; default ->
//			 * "Error al iniciar sesión, verifique su conexión"; };
//			 */
//			// Manejar la excepción de autenticación fallida (por ejemplo,
//			// usuario/contraseña incorrectos)
//			model.addAttribute("error", "Credenciales inválidas, por favor verificar.");
//			// e.printStackTrace();
//			return "loginEmpleador";
//		}
//	}
//
//	@GetMapping("/forgot-password")
//	public String showForgotPasswordForm() {
//		return "forgot-password"; // Devuelve la vista donde los usuarios pueden solicitar restablecer su
//									// contraseña
//	}
//
//	@GetMapping("/reset-password")
//	public String processResetPassword(@RequestParam("token") String token, Model model) {
//		CambioContrasenia cambioContrasenia = cambioContraseniaService.findByToken(token);
//		Usuario usuario = usuarioService.findByUsetId(cambioContrasenia.getUsuario().getUsetId());
//		if (cambioContrasenia != null && "A".equals(cambioContrasenia.getCacoEstado())) {
//			// Token válido, mostrar formulario de cambio de contraseña
//			model.addAttribute("usuario",usuario);
//			model.addAttribute("token", token);
//			model.addAttribute("mensaje", "SUCCESS");
//        } else {
//			// Token inválido o caducado
//			model.addAttribute("mensaje", "El enlace de restablecimiento de contraseña no es válido o ha caducado.");
//        }
//        return "reset-password";
//    }
//
//	@PostMapping("/reset-password")
//	public String processResetSavePassword(@RequestParam("token") String token, @RequestParam("password") String newPassword, Model model) {
//		CambioContrasenia cambioContrasenia = cambioContraseniaService.findByToken(token);
//		if (cambioContrasenia != null && "A".equals(cambioContrasenia.getCacoEstado())) {
//			// Actualizar contraseña del usuario
//			Usuario usuario = cambioContrasenia.getUsuario();
//			usuario.setUsetPassword(newPassword);
//			usuarioService.guardar(usuario);
//			// Desactivar el token
//			cambioContrasenia.setCacoEstado("I");
//			cambioContraseniaService.save(cambioContrasenia);
//			model.addAttribute("message", "La contraseña se ha restablecido con éxito.");
//        } else {
//			model.addAttribute("error", "El enlace de restablecimiento de contraseña no es válido o ha caducado.");
//        }
//        return "reset-password-success";
//    }

}
