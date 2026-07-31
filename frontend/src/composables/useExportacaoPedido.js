import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'

export function useExportacaoPedido() {
  /**
   * Formata valor para moeda brasileira
   */
  function formatCurrency(value) {
    if (value == null) return 'R$ 0,00'
    const num = Number(value)
    if (Number.isNaN(num)) return 'R$ 0,00'
    return num.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
  }

  /**
   * Formata data para padrão brasileiro
   */
  function formatDate(dateString) {
    if (!dateString) return '—'
    const date = new Date(dateString)
    return date.toLocaleDateString('pt-BR')
  }

  /**
   * Formata data e hora para padrão brasileiro
   */
  function formatDateTime(dateTimeString) {
    if (!dateTimeString) return '—'
    const date = new Date(dateTimeString)
    return date.toLocaleString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  /**
   * Retorna label do status
   */
  function getStatusLabel(status) {
    const labels = {
      RASCUNHO: 'Rascunho',
      ENVIADO: 'Enviado',
      PARCIAL: 'Parcial',
      RECEBIDO: 'Recebido',
      CANCELADO: 'Cancelado'
    }
    return labels[status] || status
  }

  /**
   * Exporta pedido para PDF
   */
  function exportarPDF(pedido) {
    const doc = new jsPDF()

    // Configurar fonte
    doc.setFont('helvetica')

    // Título
    doc.setFontSize(20)
    doc.setTextColor(107, 62, 38) // #6B3E26
    doc.text(`Pedido de Compra #${pedido.id}`, 14, 20)

    // Linha separadora
    doc.setDrawColor(107, 62, 38)
    doc.setLineWidth(0.5)
    doc.line(14, 25, 196, 25)

    // Informações do cabeçalho
    doc.setFontSize(10)
    doc.setTextColor(60, 60, 60)

    let yPos = 35

    // Status
    doc.setFont('helvetica', 'bold')
    doc.text('Status:', 14, yPos)
    doc.setFont('helvetica', 'normal')
    doc.text(getStatusLabel(pedido.status), 40, yPos)

    yPos += 7

    // Fornecedor
    if (pedido.fornecedorNome) {
      doc.setFont('helvetica', 'bold')
      doc.text('Fornecedor:', 14, yPos)
      doc.setFont('helvetica', 'normal')
      doc.text(pedido.fornecedorNome, 40, yPos)
      yPos += 7
    }

    // Data de criação
    doc.setFont('helvetica', 'bold')
    doc.text('Criado em:', 14, yPos)
    doc.setFont('helvetica', 'normal')
    doc.text(formatDateTime(pedido.criadoEm), 40, yPos)

    yPos += 7

    // Data prevista
    if (pedido.dataPrevista) {
      doc.setFont('helvetica', 'bold')
      doc.text('Data Prevista:', 14, yPos)
      doc.setFont('helvetica', 'normal')
      doc.text(formatDate(pedido.dataPrevista), 40, yPos)
      yPos += 7
    }

    // Observação
    if (pedido.observacao) {
      doc.setFont('helvetica', 'bold')
      doc.text('Observação:', 14, yPos)
      doc.setFont('helvetica', 'normal')
      const splitObs = doc.splitTextToSize(pedido.observacao, 150)
      doc.text(splitObs, 40, yPos)
      yPos += (splitObs.length * 5) + 5
    } else {
      yPos += 3
    }

    // Tabela de itens
    const itensData = (pedido.itens || []).map(item => [
      item.produtoNome,
      item.insumo ? 'Insumo' : 'Vendável',
      item.skuNome || item.embalagemNome || '—',
      item.quantidade ? item.quantidade.toFixed(3) : '—',
      formatCurrency(item.custoUnitario),
      formatCurrency(item.subtotal)
    ])

    autoTable(doc, {
      startY: yPos + 5,
      head: [['Produto', 'Tipo', 'SKU/Embalagem', 'Quantidade', 'Custo Unit.', 'Subtotal']],
      body: itensData,
      theme: 'striped',
      headStyles: {
        fillColor: [107, 62, 38], // #6B3E26
        textColor: [255, 255, 255],
        fontStyle: 'bold',
        fontSize: 9
      },
      bodyStyles: {
        fontSize: 9,
        textColor: [60, 60, 60]
      },
      columnStyles: {
        0: { cellWidth: 60 },
        1: { cellWidth: 25, halign: 'center' },
        2: { cellWidth: 35 },
        3: { cellWidth: 25, halign: 'right' },
        4: { cellWidth: 25, halign: 'right' },
        5: { cellWidth: 25, halign: 'right' }
      },
      margin: { left: 14, right: 14 }
    })

    // Totais
    const finalY = doc.lastAutoTable.finalY || yPos + 50

    doc.setFontSize(11)
    doc.setFont('helvetica', 'bold')
    doc.setTextColor(107, 62, 38)

    // Total de itens
    doc.text(`Total de Itens: ${pedido.itens?.length || 0}`, 14, finalY + 10)

    // Valor total
    const valorTotal = (pedido.itens || []).reduce((sum, item) => sum + (item.subtotal || 0), 0)
    doc.text(`Valor Total: ${formatCurrency(valorTotal)}`, 14, finalY + 17)

    // Rodapé
    const pageCount = doc.internal.getNumberOfPages()
    doc.setFontSize(8)
    doc.setFont('helvetica', 'normal')
    doc.setTextColor(150, 150, 150)

    for (let i = 1; i <= pageCount; i++) {
      doc.setPage(i)
      doc.text(
        `Gerado em ${formatDateTime(new Date().toISOString())} - Página ${i} de ${pageCount}`,
        14,
        doc.internal.pageSize.height - 10
      )
    }

    // Salvar PDF
    doc.save(`pedido-compra-${pedido.id}.pdf`)
  }

  /**
   * Exporta pedido para CSV
   */
  function exportarCSV(pedido) {
    // Cabeçalho do pedido
    const cabecalho = [
      ['Pedido de Compra', `#${pedido.id}`],
      ['Status', getStatusLabel(pedido.status)],
      ['Fornecedor', pedido.fornecedorNome || '—'],
      ['Criado em', formatDateTime(pedido.criadoEm)],
      ['Data Prevista', formatDate(pedido.dataPrevista)],
      ['Observação', pedido.observacao || '—'],
      [] // Linha vazia
    ]

    // Cabeçalho da tabela de itens
    const headerItens = ['Produto', 'Tipo', 'SKU/Embalagem', 'Quantidade', 'Custo Unitário', 'Subtotal']

    // Dados dos itens
    const itensData = (pedido.itens || []).map(item => [
      item.produtoNome,
      item.insumo ? 'Insumo' : 'Vendável',
      item.skuNome || item.embalagemNome || '—',
      item.quantidade ? item.quantidade.toFixed(3) : '—',
      item.custoUnitario ? item.custoUnitario.toFixed(2) : '—',
      item.subtotal ? item.subtotal.toFixed(2) : '—'
    ])

    // Totais
    const valorTotal = (pedido.itens || []).reduce((sum, item) => sum + (item.subtotal || 0), 0)
    const totais = [
      [],
      ['Total de Itens', pedido.itens?.length || 0],
      ['Valor Total', valorTotal.toFixed(2)]
    ]

    // Combinar tudo
    const csvData = [
      ...cabecalho,
      headerItens,
      ...itensData,
      ...totais
    ]

    // Converter para CSV
    const csvContent = csvData.map(row =>
      row.map(cell => {
        // Escapar vírgulas e aspas
        const cellStr = String(cell || '')
        if (cellStr.includes(',') || cellStr.includes('"') || cellStr.includes('\n')) {
          return `"${cellStr.replace(/"/g, '""')}"`
        }
        return cellStr
      }).join(',')
    ).join('\n')

    // Adicionar BOM para UTF-8
    const BOM = '\uFEFF'
    const blob = new Blob([BOM + csvContent], { type: 'text/csv;charset=utf-8;' })

    // Criar link para download
    const link = document.createElement('a')
    const url = URL.createObjectURL(blob)
    link.setAttribute('href', url)
    link.setAttribute('download', `pedido-compra-${pedido.id}.csv`)
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  return {
    exportarPDF,
    exportarCSV
  }
}
