#!/usr/bin/python
import state_graph


class EventSequences:
    # the event sequences leading a state to another
    dict = {'edge_id': 'event_sequence'}

    def __init__(self, length, content):

        self.length = length
        self.content = content

    # storing the shortest eventSequences leading a state transition
    def store_event_sequence(self, edge_id, events):
        if EventSequences.dict.get(edge_id, None) is None:
            EventSequences.dict[edge_id] = EventSequences(len(events), ''.join(events))

        else:
            if (EventSequences.dict.get(edge_id).length > len(events)):
                EventSequences.dict[edge_id] = EventSequences(len(events), ''.join(events))


class K_Path_Traveller:

    def __init__(self):
        self.steps = 0
        self.paths = []
        self.path = []

    def get_children(self, node):
        if node == None:
            return None
            print node
        return node.out_nodes

    def travel_DFS(self, graph, node, steps):
        if steps == 1 or len(node.out_nodes) == 0:
            self.path.append(node)
            self.paths.append(self.path[:])
            self.path.pop()
            return

        self.path.append(node)
        children = self.get_children(node)
        for child in children:
            self.travel_DFS(graph, graph.retrieve(child), steps - 1)

        self.path.pop()

    def compute_fitness_k_neighbors(self, graph, node, steps):

        if graph == None or node == None:
            raise Exception('Graph or target node is null')
        if len(graph.states) == 0:
            raise Exception('Graph is empty')
        self.travel_DFS(graph, node, steps)

        return self.paths

    def dump(self, paths):
        for p in self.paths:
            road = ""
            for node in p:
                road = road + node.uid + "-->"
            print "path: " + road


if __name__ == '__main__':
    state_graph = state_graph.StateGraph()
    state_graph.add_node('1')
    state_graph.add_node('2')
    state_graph.add_node('3')
    state_graph.add_node('4')

    state_graph.add_edge('1', '2')
    state_graph.add_edge('2', '1')
    state_graph.add_edge('2', '4')
    state_graph.add_edge('4', '1')

    node = state_graph.retrieve('4')
    #  print node

    traveller = K_Path_Traveller()
    traveller.compute_fitness_k_neighbors(state_graph, node, 1)



