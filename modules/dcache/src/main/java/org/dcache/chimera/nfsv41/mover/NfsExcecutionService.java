package org.dcache.chimera.nfsv41.mover;

import diskCacheV111.vehicles.PoolPassiveIoFileMessage;
import dmg.cells.nucleus.CellEndpoint;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellPath;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.dcache.cells.CellMessageSender;
import org.dcache.chimera.ChimeraFsException;
import org.dcache.chimera.nfs.v4.xdr.stateid4;
import org.dcache.pool.classic.Cancelable;
import org.dcache.pool.classic.CompletionHandler;
import org.dcache.pool.classic.MoverExecutorService;
import org.dcache.pool.classic.PoolIORequest;
import org.dcache.pool.classic.PoolIOTransfer;
import org.dcache.pool.movers.IoMode;
import org.dcache.pool.movers.ManualMover;
import org.dcache.pool.repository.RepositoryChannel;
import org.dcache.pool.repository.FileRepositoryChannel;
import org.dcache.pool.repository.ReplicaDescriptor;
import org.dcache.util.NetworkUtils;
import org.dcache.util.PortRange;
import org.dcache.xdr.OncRpcException;
import org.dcache.xdr.XdrBuffer;
import org.dcache.xdr.XdrEncodingStream;
import org.glassfish.grizzly.Buffer;
import org.ietf.jgss.GSSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @since 1.9.11
 */
public class NfsExcecutionService implements MoverExecutorService, CellMessageSender {

    private static final Logger _log = LoggerFactory.getLogger(NfsExcecutionService.class);
    private NFSv4MoverHandler _nfsIO;
    private CellEndpoint _cellEndpoint;
    private boolean _withGss = false;

    public void init() throws ChimeraFsException, OncRpcException, IOException, GSSException {

        String dcachePorts = System.getProperty("org.dcache.net.tcp.portrange");
        PortRange portRange;
        if (dcachePorts != null) {
            portRange = PortRange.valueOf(dcachePorts);
        } else {
            portRange = new PortRange(0);
        }

        _nfsIO = new NFSv4MoverHandler(portRange, _withGss,
                _cellEndpoint.getCellInfo().getCellName());
    }

    public void shutdown() throws IOException {
        _nfsIO.shutdown();
    }

    @Override
    public Cancelable execute(PoolIORequest request, final CompletionHandler completionHandler) {

        try {
            NFS4ProtocolInfo nfs4ProtocolInfo = (NFS4ProtocolInfo) request.getTransfer().getProtocolInfo();
            PoolIOTransfer transfer = request.getTransfer();

            stateid4 stateid = nfs4ProtocolInfo.stateId();
            ReplicaDescriptor descriptor = transfer.getIoHandle();
            String openMode = transfer.getIoMode() == IoMode.WRITE ? "rw" : "r";
            final RepositoryChannel repositoryChannel = new FileRepositoryChannel(descriptor.getFile(), openMode);

            final MoverBridge moverBridge = new MoverBridge((ManualMover) transfer.getMover(),
                    request.getPnfsId(), stateid, repositoryChannel, transfer.getIoMode(), descriptor);
            _nfsIO.addHandler(moverBridge);

            InetAddress localAddress = NetworkUtils.
                    getLocalAddress(nfs4ProtocolInfo.getSocketAddress().getAddress());

            XdrBuffer xdr = new XdrBuffer(128);
            xdr.beginEncoding();
            stateid.xdrEncode(xdr);
            xdr.endEncoding();
            Buffer buffer = xdr.body();
            byte[] d = new byte[buffer.remaining()];
            buffer.get(d);

            PoolPassiveIoFileMessage msg = new PoolPassiveIoFileMessage(request.getCellEndpoint().getCellInfo().getCellName(),
                    new InetSocketAddress(localAddress, _nfsIO.getLocalAddress().getPort()), d);

            CellPath cellpath = nfs4ProtocolInfo.door();
            request.getCellEndpoint().sendMessage(new CellMessage(cellpath, msg));

            return new Cancelable() {
                @Override
                public void cancel() {
                    _nfsIO.removeHandler(moverBridge);
                    try {
                        repositoryChannel.close();
                    } catch (IOException e) {
                        _log.error("failed to close RAF", e);
                    }
                    completionHandler.completed(null, null);
                }
            };
        } catch (Throwable e) {
            completionHandler.failed(e, null);
        }
        return null;
    }

    public void setEnableGss(boolean withGss) {
        _withGss = withGss;
    }

    @Override
    public void setCellEndpoint(CellEndpoint endpoint) {
        _cellEndpoint = endpoint;
    }
}