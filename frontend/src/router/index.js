import { route } from "quasar/wrappers";
import {
  createRouter,
  createMemoryHistory,
  createWebHistory,
  createWebHashHistory,
} from "vue-router";
import routes from "./routes";

/*
 * If not building with SSR mode, you can
 * directly export the Router instantiation;
 *
 * The function below can be async too; either use
 * async/await or return a Promise which resolves
 * with the Router instance.
 */

export default route(function (/* { store, ssrContext } */) {
  const createHistory = process.env.SERVER
    ? createMemoryHistory
    : process.env.VUE_ROUTER_MODE === "history"
    ? createWebHistory
    : createWebHashHistory;

  const Router = createRouter({
    scrollBehavior: () => ({ left: 0, top: 0 }),
    routes,

    // Leave this as is and make changes in quasar.conf.js instead!
    // quasar.conf.js -> build -> vueRouterMode
    // quasar.conf.js -> build -> publicPath
    history: createHistory(process.env.VUE_ROUTER_BASE),
  });

  // Route guard para autenticação
  Router.beforeEach((to, from, next) => {
    // Rotas que não precisam de autenticação
    const publicRoutes = ["/", "/oauth2/handler"];
    
    if (publicRoutes.includes(to.path)) {
      next();
      return;
    }

    // Verificar autenticação básica
    const token = sessionStorage.getItem("token");
    const isAuthenticated = !!token;

    if (to.meta.requiresAuth && !isAuthenticated) {
      console.log("Rota protegida sem autenticação - redirecionando para login");
      next("/");
      return;
    }

    // Para rotas protegidas, verificar se usuário tem acesso
    if (to.meta.requiresAuth && isAuthenticated) {
      const userRoles = JSON.parse(sessionStorage.getItem("user_roles") || "[]");
      const hasGroup = !!sessionStorage.getItem("user_grupo_id");
      
      // SYSTEM role sempre pode prosseguir
      if (userRoles.includes("SYSTEM")) {
        next();
        return;
      }
      
      // Outros usuários precisam de grupo
      if (!hasGroup) {
        console.warn("Usuário autenticado sem grupo definido");
        // Permitir acesso mas com warning - a aplicação decidirá o que fazer
        next();
        return;
      }
    }
    
    next();
  });

  return Router;
});
