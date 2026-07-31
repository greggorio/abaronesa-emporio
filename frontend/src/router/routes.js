import Authentication from "../components/AuthenticationPage.vue";
import MainLayout from "../layouts/MainLayout.vue";
import OAuth2Handler from "../pages/OAuth2Handler.vue";
import TaskBoard from "../components/tasks/TaskBoard.vue";

const routes = [
  {
    path: "/",
    name: "auth",
    component: Authentication,
  },
  {
    path: "/",
    component: MainLayout,
    children: [
      {
        path: "/home",
        name: "home",
        component: () => import("../pages/DashboardPage.vue"),
      },
      {
        path: "/container",
        name: "container",
        component: () => import("../components/forms/ProgramsPage.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "/configuracoes",
        name: "configuracoes",
        component: () => import("../components/configuracoes/PainelControle.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "/configuracoes/atualizacao-sistema",
        name: "configuracoes-atualizacao-sistema",
        component: () => import("../pages/ProductionDeploymentPage.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "/pedidos-compra",
        name: "pedidos-compra",
        component: () => import("../pages/PedidosCompraPage.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "/producao",
        name: "producao",
        component: () => import("@/pages/ProducaoPage.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "/form-builder",
        name: "form-builder",
        component: () => import("../pages/admin/FormBuilder.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "/tasks",
        name: "tasks",
        component: TaskBoard,
        meta: { requiresAuth: true },
      },
      {
        path: "/agenda-execucao",
        name: "agenda-execucao",
        component: () => import("../pages/AgendaExecucaoPage.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "/validade/operacao",
        name: "validade-operacao",
        component: () => import("../pages/mobile/ValidadeTarefaMobilePage.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "/validade/painel",
        name: "validade-painel",
        component: () => import("../pages/ValidadeDashboardPage.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "/validade/historico",
        name: "validade-historico",
        component: () => import("../pages/ValidadeHistoricoPage.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "/mobile/validade-tarefas",
        redirect: "/validade/operacao",
        meta: { requiresAuth: true },
      },
      {
        path: "/mobile/validade/tarefa/:id",
        name: "validade-mobile-tarefa",
        component: () => import("../pages/mobile/ValidadeTarefaExecPage.vue"),
        meta: { requiresAuth: true },
      },
    ],
  },
  {
    path: "/oauth2/handler",
    name: "oauth2-handler",
    component: OAuth2Handler,
  },
  {
    path: "/:catchAll(.*)*",
    component: () => import("pages/ErrorNotFound.vue"),
  },
];

export default routes;
