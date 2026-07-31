<script setup>
import { useApiRequest } from "@/composables/useApiRequest";
import { onMounted } from "vue";

const { apiRequest } = useApiRequest();

async function criarInventario() {
  const response = await apiRequest("/api/inventarios/", "POST", {
    // Campos básicos do inventário
    descricao: "Inventário Geral - Depósito Central",
    tipo: "GERAL", // Enum: GERAL ou outro valor definido
    idUsuarioCriacao: 1001, // ID do usuário logado que está criando o inventário
    permitirFinalizacaoSemAceite: false,

    // Campos opcionais que podem ser necessários
    dataPrevisaoFinalizacao: "2025-05-15",
    observacao: "Contagem trimestral de estoque no depósito principal",

    // Filtros para o inventário (se aplicável)
    filtroCategoria: 5, // ID da categoria de produtos
    filtroFornecedor: 12, // ID do fornecedor
    filtroLocalEstoque: 3, // ID do local de estoque

    // Se você estiver enviando itens junto com o inventário
    itens: [
      {
        idProduto: 1456,
        quantidadeEsperada: 120.5,
        unidadeMedida: "UN",
      },
      {
        idProduto: 1789,
        quantidadeEsperada: 45.0,
        unidadeMedida: "CX",
      },
    ],
  });

  if (response.status === 201) {
    console.log("Inventário criado com sucesso:", JSON.stringify(response.data));
  } else {
    console.error("Erro ao criar inventário:", response.status, response.data);
  }
}

onMounted(() => {
  criarInventario()
    .then(() => {
      console.log("Inventário criado com sucesso!");
    })
    .catch((error) => {
      console.error("Erro ao criar inventário:", error);
    });
});
</script>
