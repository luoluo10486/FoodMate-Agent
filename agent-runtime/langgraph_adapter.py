"""可选 LangGraph 适配层。

M1-4 的业务规则仍由 agent_core 的白名单状态图定义；安装 LangGraph 后本适配层
只负责把相同节点和边注册到 StateGraph，模型不能通过图状态动态增加边。
"""

from __future__ import annotations

from typing import Any

from agent_core import WORKFLOW_EDGES


def build_graph():
    """返回编译后的 LangGraph；未安装依赖时明确失败，不伪装成本地实现。"""
    try:
        from langgraph.graph import END, START, StateGraph
    except ImportError as error:
        raise RuntimeError("LANGGRAPH_NOT_INSTALLED") from error

    graph = StateGraph(dict)
    nodes = {node for node in WORKFLOW_EDGES if node not in {"start", "terminal"}}
    for node in nodes:
        graph.add_node(node, lambda state, _node=node: {"node": _node, **state})
    graph.add_edge(START, "router")
    for source, targets in WORKFLOW_EDGES.items():
        if source in {"start", "terminal"}:
            continue
        for target in targets:
            if target == "terminal":
                graph.add_edge(source, END)
            elif target != "start":
                graph.add_edge(source, target)
    return graph.compile()
