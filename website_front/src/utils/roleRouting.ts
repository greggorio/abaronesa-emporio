export type RoleRedirectRule = {
  roles: string[];
  path: string;
};

const redirectRules: RoleRedirectRule[] = [
  { roles: ['KDS'], path: '/kds' },
  { roles: ['WAITER'], path: '/waiter' },
  { roles: ['CAIXA'], path: '/waiter' },
  { roles: ['ADMIN', 'SYSTEM', 'FUNCIONARIO'], path: '/admin/dashboard' },
  { roles: ['CLIENTE'], path: '/areacliente' },
];

/**
 * Retorna o primeiro caminho válido baseado na prioridade das roles recebidas.
 */
export function getRedirectPathByRoles(roles?: string[]): string {
  if (!roles || roles.length === 0) {
    return '/';
  }

  for (const rule of redirectRules) {
    if (rule.roles.some((role) => roles.includes(role))) {
      return rule.path;
    }
  }

  return '/';
}

/**
 * Verifica se um conjunto de roles contém qualquer uma das exigidas.
 */
export function hasRequiredRole(roles?: string[], requiredRoles?: string | string[]): boolean {
  if (!requiredRoles) {
    return true;
  }
  if (!roles || roles.length === 0) {
    return false;
  }

  const normalized = Array.isArray(requiredRoles) ? requiredRoles : [requiredRoles];
  return normalized.some((required) => roles.includes(required));
}
