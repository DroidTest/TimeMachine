#!/usr/bin/python

from collections import deque
from state_graph import StateGraph
from utils import K_Path_Traveller

class CircularRestoreStrategy:

    # The graph data structure and the desired size of the recent restore path
    def __init__(self, state_graph, num_recent_restores):
        self.state_graph = state_graph

        self.recent_restores_size = num_recent_restores
        self.recent_restores = deque(maxlen=num_recent_restores)

    # Returns uid of fittest state
    def get_fittest_state(self):
        fittest_candidate = self.state_graph.get_fittest_state(0)
        candidates = self.state_graph.get_nfittest(self.recent_restores_size + 1)

        # Common case: len(candidates) > self.recent_restores_size
        # There us at least one candidate state not in the list of recently restored states
        for id, candidate in candidates:
            if candidate.uid not in self.recent_restores:
                self.recent_restores.append(candidate.uid)
                return candidate.uid

        # No suitable candidate (new state to restore) found, so restore the fittest.
        # If dequeue is empty, then add state to dequeue, otherwise, duplicate and only return.
        if len(self.recent_restores) == 0:
            self.recent_restores.append(fittest_candidate.uid)

        return fittest_candidate.uid

    def __str__(self):
        format = "Circular Queue Strategy\nSize of recent restore queue is " + str(self.recent_restores_size) + "\n"
        format = format + "Recent restore queue is " + str(self.recent_restores) + "\n"
        return format


    def compute_k_neighbours_fittness(self, node, steps):
        traveller = K_Path_Traveller()
        paths = traveller.compute_fitness_k_neighbors(self.state_graph, node, steps)
        # traveller.dump(paths)

        if len(paths) == 0:
            raise Exception('no available paths')
        total_fitness = 0
        for path in paths:
            for state in path:
                total_fitness = total_fitness + state.fitness_score

        return total_fitness / len(paths)


    def get_k_neighbours_fittest_state(self):

        snapshots = self.state_graph.get_snapshots()
        fittest_node = None
        highest_score = 0

        for item in snapshots:
            s = item[1]
            k_score = self.compute_k_neighbours_fittness(s, self.recent_restores_size)
            if s.fitness_score > highest_score:
                fittest_node = s
                highest_score = s.fitness_score

                print s.uid + " k_neighbour_fitness: " + str(s)
        return fittest_node.uid


# Some tests
if __name__ == '__main__':

    # Initial case -> empty
    graph = StateGraph()
    graph.add_node('1')
    node = graph.retrieve('1')
    node.fitness_score = 100
    graph.dump()

    strategy = CircularRestoreStrategy(graph, 3)
    print strategy.get_fittest_state()  # expected state 1
    print strategy
    print strategy.get_fittest_state()  # expected state 1
    print strategy

    # Building up, graph has less states than size of queue
    graph.add_node('2')
    node = graph.retrieve('2')
    node.fitness_score = 90
    graph.dump()
    print strategy.get_fittest_state()  # expected state 2
    print strategy

    # Most usual case (larger number of fit states than size of queue)
    graph = StateGraph()
    for i in range(0, 7):
        graph.add_node(str(i))
        node = graph.retrieve(str(i))
        node.fitness_score = 100 - (10 * i)

    graph.dump()
    strategy = CircularRestoreStrategy(graph, 3)

    # Fittest 4 states [0, 1, 2]
    print strategy.get_fittest_state()  # expected state 0
    print strategy
    print strategy.get_fittest_state()  # expected state 1
    print strategy
    print strategy.get_fittest_state()  # expected state 2
    print strategy
    print strategy.get_fittest_state()  # expected state 3
    print strategy
    print strategy.get_fittest_state()  # expected state 0
    print strategy
    print strategy.get_fittest_state()  # expected state 1
    print strategy
    print strategy.get_fittest_state()  # expected state 2
    print strategy

    node = graph.retrieve('6')
    node.fitness_score = 101
    graph.dump()
    # Fittest 4 states [6, 0, 1, 2]
    print strategy.get_fittest_state()  # expected state 6
    print strategy
    print strategy.get_fittest_state()  # expected state 0
    print strategy
