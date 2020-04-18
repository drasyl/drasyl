package org.drasyl.core.client;

import akka.actor.ActorSystem;
import akka.actor.Deployer;
import akka.actor.DynamicAccess;

/**
 * Deployer maps actor paths to actor deployments.
 */
public class P2PDeployer extends Deployer {
    public P2PDeployer(ActorSystem.Settings settings, DynamicAccess dynamicAccess) {
        super(settings, dynamicAccess);
    }
}
