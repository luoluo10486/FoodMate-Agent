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
    nodes = {"router", "planner", "execution", "validator", "composer", "eval"}
    for node in nodes:
        graph.add_node(node, lambda state, _node=node: {"node": _node, **state})
    graph.add_edge(START, "router")
    # 适配层先编译一条无副作用的复杂任务主路径；真实条件判断仍由 agent_core
    # 在进入 LangGraph 前完成，避免模型通过动态 edge 改写白名单。
    graph.add_conditional_edges("router", lambda _state: "planner", {"planner": "planner"})
    graph.add_edge("planner", "execution")
    graph.add_edge("execution", "validator")
    graph.add_edge("validator", "composer")
    graph.add_edge("composer", "eval")
    graph.add_edge("eval", END)
    return graph.compile()
